import { describe, expect, it } from "vitest";
import { decodeIcloudToken, signSession, verifySession } from "../src/session";

function b64url(obj: unknown): string {
  return btoa(JSON.stringify(obj)).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

function icloudToken(payload: Record<string, unknown>): string {
  return `${b64url({ alg: "HS256", typ: "JWT" })}.${b64url(payload)}.signature`;
}

describe("decodeIcloudToken", () => {
  it("reads admno/name/email from the payload", () => {
    const claims = decodeIcloudToken(icloudToken({ admno: "21001", name: "Aksh", email: "a@b.com", exp: 9_999_999_999 }));
    expect(claims).toMatchObject({ admno: "21001", name: "Aksh", email: "a@b.com", exp: 9_999_999_999 });
  });

  it("falls back to preferred_username when admno is blank", () => {
    expect(decodeIcloudToken(icloudToken({ admno: "", preferred_username: "GU123", exp: 1 }))?.admno).toBe("GU123");
  });

  it("returns null for structurally invalid tokens", () => {
    expect(decodeIcloudToken("nope")).toBeNull();
    expect(decodeIcloudToken("aaaa.notbase64json.bbbb")).toBeNull();
    expect(decodeIcloudToken(icloudToken({ name: "no admno" }))).toBeNull();
  });
});

describe("Axis session token", () => {
  const secret = "unit-test-secret";

  it("round-trips a valid token", async () => {
    const token = await signSession("21001", "admin", secret, 3600);
    expect(await verifySession(token, secret)).toMatchObject({ admno: "21001", role: "admin" });
  });

  it("rejects a token signed with a different secret", async () => {
    const token = await signSession("21001", "user", secret, 3600);
    expect(await verifySession(token, "other-secret")).toBeNull();
  });

  it("rejects a token whose payload was tampered with (privilege escalation attempt)", async () => {
    const token = await signSession("21001", "user", secret, 3600);
    const parts = token.split(".");
    const forged = b64url({ admno: "21001", role: "admin", iat: 0, exp: 9_999_999_999 });
    expect(await verifySession(`${parts[0]}.${forged}.${parts[2]}`, secret)).toBeNull();
  });

  it("rejects an expired token", async () => {
    const token = await signSession("21001", "user", secret, -10);
    expect(await verifySession(token, secret)).toBeNull();
  });
});
