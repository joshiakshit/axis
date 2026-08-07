// The remote-config contract shared between the Axis app and this Worker.
//
// The app fetches this on launch. Two fields carry the whole point of the service:
//   - `authToken`  : the static iCloudEMS bearer the app sends to authorize OTP/login/refresh.
//                    If iCloudEMS rotates it, we update KV here and every client picks it up — no APK.
//   - `appVersion` : the `appversion` string the app sends to iCloudEMS on OTP calls (same rotation risk).
//
// `authToken` is optional: when absent the app keeps using its compiled-in BuildConfig token, so the
// service is purely additive — the app works even if this endpoint is down or unset.

export interface RemoteConfig {
  /** iCloudEMS static bearer. Omitted when unset (app falls back to its baked-in token). */
  authToken?: string;
  /** `appversion` string sent to iCloudEMS on OTP calls. */
  appVersion: string;
  /** Force-update floor: the app blocks if its BuildConfig.VERSION_CODE < this. */
  minSupportedVersionCode: number;
  /** Newest build available (for a soft "update available" nudge). */
  latestVersionCode: number;
  latestVersionName: string;
  /** Where to send users to update (store/APK link). */
  updateUrl: string;
  /** Hard stop: when true the app shows `message` and blocks use. */
  killSwitch: boolean;
  /** Optional user-facing notice (kill-switch reason, maintenance banner, …). */
  message: string;
  /** Stamped server-side on every write. */
  updatedAt: string;
}

/** Env vars / secrets available to the Worker (declared in wrangler.toml + `wrangler secret put`). */
export interface Env {
  CONFIG: KVNamespace;
  /** Fallback appVersion when KV is empty. */
  DEFAULT_APP_VERSION?: string;
  /** Default tenant key when `?tenant=` is omitted. */
  DEFAULT_TENANT?: string;
  /** Optional secret: seeds `authToken` before anything is written to KV. */
  DEFAULT_AUTH_TOKEN?: string;
  /** Secret: bearer required to write config. Writes are disabled until this is set. */
  ADMIN_TOKEN?: string;
  /** Optional secret: when set, GET /v1/config requires a matching `x-axis-key` header. */
  APP_ACCESS_KEY?: string;
}

const EPOCH = new Date(0).toISOString();

/** Baseline config used when KV holds nothing yet. */
export function defaultConfig(env: Env): RemoteConfig {
  const base: RemoteConfig = {
    appVersion: env.DEFAULT_APP_VERSION?.trim() || "3.0.3",
    minSupportedVersionCode: 1,
    latestVersionCode: 1,
    latestVersionName: "1.0.0",
    updateUrl: "",
    killSwitch: false,
    message: "",
    updatedAt: EPOCH,
  };
  const seed = env.DEFAULT_AUTH_TOKEN?.trim();
  return seed ? { ...base, authToken: seed } : base;
}

/**
 * Parse a stored KV value, filling any missing keys from defaults so old records survive schema growth.
 * A corrupt/unparseable value degrades to defaults rather than throwing.
 */
export function parseStored(raw: string | null, env: Env): RemoteConfig {
  const base = defaultConfig(env);
  if (!raw) return base;
  let stored: unknown;
  try {
    stored = JSON.parse(raw);
  } catch {
    return base;
  }
  if (typeof stored !== "object" || stored === null) return base;
  return { ...base, ...(stored as Partial<RemoteConfig>) };
}

function isInt(value: unknown): value is number {
  return typeof value === "number" && Number.isInteger(value) && value >= 0;
}

/**
 * Validate an admin-supplied patch and merge it onto `current`. Partial patches are allowed; only the
 * provided keys change. `authToken: null` clears the override. Returns the next config plus any field
 * errors (a non-empty `errors` list means the caller should reject with 400 and not write).
 */
export function applyPatch(
  current: RemoteConfig,
  patch: unknown,
): { next: RemoteConfig; errors: string[] } {
  const errors: string[] = [];
  const next: RemoteConfig = { ...current };

  if (typeof patch !== "object" || patch === null) {
    return { next, errors: ["body must be a JSON object"] };
  }
  const p = patch as Record<string, unknown>;

  if ("authToken" in p) {
    const v = p.authToken;
    if (v === null || v === "") {
      delete next.authToken;
    } else if (typeof v === "string") {
      next.authToken = v;
    } else {
      errors.push("authToken must be a string or null");
    }
  }
  if ("appVersion" in p) {
    if (typeof p.appVersion === "string" && p.appVersion.trim() !== "") {
      next.appVersion = p.appVersion.trim();
    } else {
      errors.push("appVersion must be a non-empty string");
    }
  }
  if ("minSupportedVersionCode" in p) {
    if (isInt(p.minSupportedVersionCode)) next.minSupportedVersionCode = p.minSupportedVersionCode as number;
    else errors.push("minSupportedVersionCode must be a non-negative integer");
  }
  if ("latestVersionCode" in p) {
    if (isInt(p.latestVersionCode)) next.latestVersionCode = p.latestVersionCode as number;
    else errors.push("latestVersionCode must be a non-negative integer");
  }
  if ("latestVersionName" in p) {
    if (typeof p.latestVersionName === "string") next.latestVersionName = p.latestVersionName;
    else errors.push("latestVersionName must be a string");
  }
  if ("updateUrl" in p) {
    if (typeof p.updateUrl === "string") next.updateUrl = p.updateUrl;
    else errors.push("updateUrl must be a string");
  }
  if ("killSwitch" in p) {
    if (typeof p.killSwitch === "boolean") next.killSwitch = p.killSwitch;
    else errors.push("killSwitch must be a boolean");
  }
  if ("message" in p) {
    if (typeof p.message === "string") next.message = p.message;
    else errors.push("message must be a string");
  }

  if (errors.length === 0) next.updatedAt = new Date().toISOString();
  return { next, errors };
}

/**
 * Cheap deterministic ETag (FNV-1a over the serialized body). Good enough to power conditional GETs and
 * avoid re-sending an unchanged config; it is not a security primitive.
 */
export function weakEtag(body: string): string {
  let hash = 0x811c9dc5;
  for (let i = 0; i < body.length; i++) {
    hash ^= body.charCodeAt(i);
    hash = Math.imul(hash, 0x01000193);
  }
  return `W/"${(hash >>> 0).toString(16)}"`;
}
