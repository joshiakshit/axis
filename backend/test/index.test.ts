import { describe, expect, it } from "vitest";
import worker from "../src/index";
import { Env } from "../src/config";
import { FakeD1, FakeKV, fakeIcloudToken } from "./fakes";

const SECRET = "test-session-secret";

function makeEnv(overrides: Partial<Env> = {}): Env {
  return {
    CONFIG: new FakeKV(),
    DB: new FakeD1(),
    DEFAULT_APP_VERSION: "3.0.3",
    DEFAULT_TENANT: "gu",
    SESSION_SECRET: SECRET,
    ADMIN_ADMNOS: "21000",
    ...overrides,
  } as unknown as Env;
}

function req(path: string, init: RequestInit = {}): Request {
  return new Request(`https://axis.test${path}`, init);
}

/** Log the owner in and return the admin session token the app would hold. */
async function adminSession(env: Env): Promise<string> {
  const res = await worker.fetch(
    req("/v1/session", { method: "POST", body: JSON.stringify({ token: fakeIcloudToken("21000") }) }),
    env,
  );
  const body = (await res.json()) as { role: string; sessionToken: string };
  expect(body.role).toBe("admin");
  return body.sessionToken;
}

describe("POST /v1/session", () => {
  it("approves the owner and persists telemetry", async () => {
    const env = makeEnv();
    const res = await worker.fetch(
      req("/v1/session", {
        method: "POST",
        body: JSON.stringify({
          token: fakeIcloudToken("21000"),
          appVersionName: "1.3.0",
          appVersionCode: 7,
          deviceModel: "Pixel 8",
          androidSdk: 34,
        }),
      }),
      env,
    );
    expect(res.status).toBe(200);
    const body = (await res.json()) as { status: string; role: string; sessionToken?: string };
    expect(body).toMatchObject({ status: "approved", role: "admin" });
    expect(body.sessionToken).toBeTruthy();

    // Telemetry landed and is visible to the admin list.
    const list = await worker.fetch(
      req("/v1/admin/users", { headers: { authorization: `Bearer ${body.sessionToken}` } }),
      env,
    );
    const users = ((await list.json()) as { users: Array<{ app_version_code: number; device_model: string }> }).users;
    expect(users[0]).toMatchObject({ app_version_code: 7, device_model: "Pixel 8" });
  });

  it("holds a brand-new user as pending with no token", async () => {
    const res = await worker.fetch(
      req("/v1/session", { method: "POST", body: JSON.stringify({ token: fakeIcloudToken("21001") }) }),
      makeEnv(),
    );
    const body = (await res.json()) as { status: string; sessionToken?: string };
    expect(body.status).toBe("pending");
    expect(body.sessionToken).toBeUndefined();
  });
});

describe("/v1/admin/config", () => {
  it("lets an admin read and set the force-update floor", async () => {
    const env = makeEnv();
    const token = await adminSession(env);

    const put = await worker.fetch(
      req("/v1/admin/config", {
        method: "PUT",
        headers: { authorization: `Bearer ${token}` },
        body: JSON.stringify({ minSupportedVersionCode: 12, latestVersionCode: 12, latestVersionName: "1.2.0" }),
      }),
      env,
    );
    expect(put.status).toBe(200);
    expect(((await put.json()) as { minSupportedVersionCode: number }).minSupportedVersionCode).toBe(12);

    const get = await worker.fetch(
      req("/v1/admin/config", { headers: { authorization: `Bearer ${token}` } }),
      env,
    );
    expect(((await get.json()) as { latestVersionName: string }).latestVersionName).toBe("1.2.0");
  });

  it("rejects a non-admin (no session) with 401", async () => {
    const res = await worker.fetch(
      req("/v1/admin/config", { method: "PUT", body: JSON.stringify({ minSupportedVersionCode: 5 }) }),
      makeEnv(),
    );
    expect(res.status).toBe(401);
  });

  it("validates the patch", async () => {
    const env = makeEnv();
    const token = await adminSession(env);
    const res = await worker.fetch(
      req("/v1/admin/config", {
        method: "PUT",
        headers: { authorization: `Bearer ${token}` },
        body: JSON.stringify({ minSupportedVersionCode: -1 }),
      }),
      env,
    );
    expect(res.status).toBe(400);
  });
});

describe("/v1/apk", () => {
  it("503s when no R2 bucket is bound", async () => {
    const res = await worker.fetch(req("/v1/apk"), makeEnv());
    expect(res.status).toBe(503);
  });
});

describe("auto-approve by prefix", () => {
  it("approves a new admno that matches autoApprovePrefix", async () => {
    const env = makeEnv();
    const token = await adminSession(env);
    await worker.fetch(
      req("/v1/admin/config", {
        method: "PUT",
        headers: { authorization: `Bearer ${token}` },
        body: JSON.stringify({ autoApprovePrefix: "024GUSCSE" }),
      }),
      env,
    );
    const res = await worker.fetch(
      req("/v1/session", { method: "POST", body: JSON.stringify({ token: fakeIcloudToken("024GUSCSE999") }) }),
      env,
    );
    expect(((await res.json()) as { status: string }).status).toBe("approved");
  });
});

describe("/v1/admin actions", () => {
  it("bans and approve-all, and reports health", async () => {
    const env = makeEnv();
    const token = await adminSession(env);
    const auth = { authorization: `Bearer ${token}` };
    // A pending user shows up, gets banned.
    await worker.fetch(req("/v1/session", { method: "POST", body: JSON.stringify({ token: fakeIcloudToken("30001") }) }), env);
    const ban = await worker.fetch(req("/v1/admin/users/30001/ban", { method: "POST", headers: auth }), env);
    expect(((await ban.json()) as { status: string }).status).toBe("banned");

    // A second pending user, then approve-all (bans are untouched).
    await worker.fetch(req("/v1/session", { method: "POST", body: JSON.stringify({ token: fakeIcloudToken("30002") }) }), env);
    const all = await worker.fetch(req("/v1/admin/approve-all", { method: "POST", headers: auth }), env);
    expect(((await all.json()) as { approved: number }).approved).toBe(1);

    const health = await worker.fetch(req("/v1/admin/health", { headers: auth }), env);
    const h = (await health.json()) as { approved: number; banned: number; apkUploaded: boolean };
    expect(h.banned).toBe(1);
    expect(h.apkUploaded).toBe(false);
  });

  it("cannot ban an admin", async () => {
    const env = makeEnv();
    const token = await adminSession(env);
    const res = await worker.fetch(
      req("/v1/admin/users/21000/ban", { method: "POST", headers: { authorization: `Bearer ${token}` } }),
      env,
    );
    expect(res.status).toBe(403);
  });
});

describe("/v1/events", () => {
  it("counts usage from a valid session and rejects without one", async () => {
    const env = makeEnv();
    const token = await adminSession(env);
    const ok = await worker.fetch(
      req("/v1/events", {
        method: "POST",
        headers: { authorization: `Bearer ${token}` },
        body: JSON.stringify({ events: [{ name: "qr_scan" }, { name: "export_pdf", count: 2 }] }),
      }),
      env,
    );
    expect(((await ok.json()) as { counted: number }).counted).toBe(2);

    const health = await worker.fetch(
      req("/v1/admin/health", { headers: { authorization: `Bearer ${token}` } }),
      env,
    );
    expect(((await health.json()) as { metrics: Record<string, number> }).metrics.qr_scan).toBe(1);

    const noauth = await worker.fetch(req("/v1/events", { method: "POST", body: JSON.stringify({ events: [] }) }), env);
    expect(noauth.status).toBe(401);
  });
});
