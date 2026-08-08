import { describe, expect, it } from "vitest";
import { Env } from "../src/config";
import { IcloudClaims } from "../src/session";
import { approveAllPending, bumpMetrics, getMetrics, listUsers, setStatus, upsertOnSession } from "../src/users";
import { FakeD1 } from "./fakes";

function env(): Env {
  return { DB: new FakeD1() } as unknown as Env;
}

function claims(admno: string): IcloudClaims {
  return { admno, name: `Name ${admno}`, email: `${admno}@gu.edu`, exp: 9_999_999_999 };
}

describe("upsertOnSession", () => {
  it("creates a new non-admin user as pending", async () => {
    const e = env();
    const user = await upsertOnSession(e, claims("21001"), false, false);
    expect(user).toMatchObject({ status: "pending", role: "user" });
  });

  it("creates an owner (allowlisted) as approved admin", async () => {
    const user = await upsertOnSession(env(), claims("21000"), true, false);
    expect(user).toMatchObject({ status: "approved", role: "admin" });
  });

  it("auto-approves a new user when open enrollment is on", async () => {
    const user = await upsertOnSession(env(), claims("21002"), false, true);
    expect(user).toMatchObject({ status: "approved", role: "user" });
  });

  it("forces an existing user to admin+approved when they become an owner", async () => {
    const e = env();
    await upsertOnSession(e, claims("21003"), false, false); // pending user
    const user = await upsertOnSession(e, claims("21003"), true, false);
    expect(user).toMatchObject({ status: "approved", role: "admin" });
  });

  it("refreshes last_seen without changing status on a repeat session", async () => {
    const e = env();
    const first = await upsertOnSession(e, claims("21004"), false, false);
    const second = await upsertOnSession(e, claims("21004"), false, false);
    expect(second.status).toBe("pending");
    expect(second.last_seen_at >= first.last_seen_at).toBe(true);
  });

  it("counts launches and records the latest telemetry", async () => {
    const e = env();
    const meta = { appVersionName: "1.2.0", appVersionCode: 5, deviceModel: "SM-A156E", androidSdk: 34 };
    const first = await upsertOnSession(e, claims("21008"), false, false, meta);
    expect(first).toMatchObject({ session_count: 1, app_version_code: 5, device_model: "SM-A156E" });
    const second = await upsertOnSession(e, claims("21008"), false, false, { ...meta, appVersionCode: 6 });
    expect(second.session_count).toBe(2);
    expect(second.app_version_code).toBe(6);
  });
});

describe("setStatus and listUsers", () => {
  it("allow then kick flips status and returns null for unknown admno", async () => {
    const e = env();
    await upsertOnSession(e, claims("21005"), false, false);
    expect((await setStatus(e, "21005", "approved"))?.status).toBe("approved");
    expect((await setStatus(e, "21005", "pending"))?.status).toBe("pending");
    expect(await setStatus(e, "does-not-exist", "approved")).toBeNull();
  });

  it("lists pending users first", async () => {
    const e = env();
    await upsertOnSession(e, claims("21006"), true, false); // approved admin
    await upsertOnSession(e, claims("21007"), false, false); // pending
    const users = await listUsers(e);
    expect(users[0].status).toBe("pending");
  });

  it("stamps approved_at only on the first approval", async () => {
    const e = env();
    await upsertOnSession(e, claims("21010"), false, false);
    const approved = await setStatus(e, "21010", "approved");
    expect(approved?.approved_at).not.toBe("");
    const kicked = await setStatus(e, "21010", "pending");
    expect(kicked?.approved_at).toBe(approved?.approved_at); // preserved, not cleared
  });

  it("keeps a banned user banned across re-logins (unlike a kick)", async () => {
    const e = env();
    await upsertOnSession(e, claims("21011"), false, true); // approved
    await setStatus(e, "21011", "banned");
    const relogin = await upsertOnSession(e, claims("21011"), false, true); // even with auto-approve on
    expect(relogin.status).toBe("banned");
  });

  it("approveAllPending flips every pending user", async () => {
    const e = env();
    await upsertOnSession(e, claims("21012"), false, false);
    await upsertOnSession(e, claims("21013"), false, false);
    await upsertOnSession(e, claims("21014"), true, false); // admin, already approved
    expect(await approveAllPending(e)).toBe(2);
    expect((await listUsers(e)).every((u) => u.status !== "pending")).toBe(true);
  });
});

describe("metrics", () => {
  it("bumps and reads aggregate counters", async () => {
    const e = env();
    await bumpMetrics(e, [{ name: "qr_scan", count: 1 }, { name: "export_pdf", count: 2 }]);
    await bumpMetrics(e, [{ name: "qr_scan", count: 3 }]);
    expect(await getMetrics(e)).toEqual({ qr_scan: 4, export_pdf: 2 });
  });
});
