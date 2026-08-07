import { describe, expect, it } from "vitest";
import { Env, applyPatch, defaultConfig, parseStored, weakEtag } from "../src/config";

// A minimal Env; the KV binding is unused by the pure helpers under test.
const env = { DEFAULT_APP_VERSION: "3.0.3", DEFAULT_TENANT: "gu" } as unknown as Env;

describe("defaultConfig", () => {
  it("uses the env appVersion and omits authToken when unseeded", () => {
    const c = defaultConfig(env);
    expect(c.appVersion).toBe("3.0.3");
    expect(c.killSwitch).toBe(false);
    expect("authToken" in c).toBe(false);
  });

  it("seeds authToken from DEFAULT_AUTH_TOKEN when present", () => {
    const c = defaultConfig({ ...env, DEFAULT_AUTH_TOKEN: "seed-token" } as Env);
    expect(c.authToken).toBe("seed-token");
  });
});

describe("parseStored", () => {
  it("returns defaults for null or corrupt input", () => {
    expect(parseStored(null, env).appVersion).toBe("3.0.3");
    expect(parseStored("{not json", env).appVersion).toBe("3.0.3");
    expect(parseStored("42", env).appVersion).toBe("3.0.3");
  });

  it("fills missing keys from defaults (schema growth is safe)", () => {
    const merged = parseStored(JSON.stringify({ appVersion: "9.9.9" }), env);
    expect(merged.appVersion).toBe("9.9.9");
    expect(merged.minSupportedVersionCode).toBe(1); // filled from defaults
    expect(merged.killSwitch).toBe(false);
  });
});

describe("applyPatch", () => {
  const base = defaultConfig(env);

  it("merges only the provided keys and stamps updatedAt", () => {
    const { next, errors } = applyPatch(base, { appVersion: "3.1.0", killSwitch: true });
    expect(errors).toEqual([]);
    expect(next.appVersion).toBe("3.1.0");
    expect(next.killSwitch).toBe(true);
    expect(next.minSupportedVersionCode).toBe(base.minSupportedVersionCode); // untouched
    expect(next.updatedAt).not.toBe(base.updatedAt);
  });

  it("sets and clears authToken", () => {
    const set = applyPatch(base, { authToken: "rotated-bearer" });
    expect(set.next.authToken).toBe("rotated-bearer");
    const cleared = applyPatch(set.next, { authToken: null });
    expect("authToken" in cleared.next).toBe(false);
    const clearedEmpty = applyPatch(set.next, { authToken: "" });
    expect("authToken" in clearedEmpty.next).toBe(false);
  });

  it("rejects bad field types without mutating on error", () => {
    const { next, errors } = applyPatch(base, { appVersion: "", minSupportedVersionCode: -1, killSwitch: "yes" });
    expect(errors.length).toBe(3);
    expect(next.updatedAt).toBe(base.updatedAt); // updatedAt only advances on a clean patch
  });

  it("rejects a non-object body", () => {
    expect(applyPatch(base, "nope").errors).toContain("body must be a JSON object");
    expect(applyPatch(base, null).errors.length).toBeGreaterThan(0);
  });
});

describe("weakEtag", () => {
  it("is stable for equal input and differs for changed input", () => {
    expect(weakEtag("a")).toBe(weakEtag("a"));
    expect(weakEtag("a")).not.toBe(weakEtag("b"));
    expect(weakEtag("").startsWith('W/"')).toBe(true);
  });
});
