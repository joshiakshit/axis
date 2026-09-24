export interface RemoteConfig {
  // Use the compiled-in token when no override is set.
  authToken?: string;
  // iCloudEMS OTP appversion, separate from the Axis app version.
  appVersion: string;
  // Block app versions below this code.
  minSupportedVersionCode: number;
  latestVersionCode: number;
  latestVersionName: string;
  updateUrl: string;
  killSwitch: boolean;
  message: string;
  notice: string;
  // Comma-separated admission number prefixes. Blank disables auto-approval.
  autoApprovePrefix: string;
  updatedAt: string;
}

export interface Env {
  CONFIG: KVNamespace;
  DB: D1Database;
  APK?: R2Bucket;
  DEFAULT_APP_VERSION?: string;
  DEFAULT_TENANT?: string;
  // "true" enables auto-approval for new users.
  OPEN_ENROLLMENT?: string;
  DEFAULT_AUTH_TOKEN?: string;
  ADMIN_TOKEN?: string;
  APP_ACCESS_KEY?: string;
  SESSION_SECRET?: string;
  // Comma-separated owner admission numbers.
  ADMIN_ADMNOS?: string;
  ADMIN_APP_TOKEN?: string;
}

const EPOCH = new Date(0).toISOString();

export function defaultConfig(env: Env): RemoteConfig {
  const base: RemoteConfig = {
    appVersion: env.DEFAULT_APP_VERSION?.trim() || "3.0.9",
    minSupportedVersionCode: 1,
    latestVersionCode: 1,
    latestVersionName: "1.0.0",
    updateUrl: "",
    killSwitch: false,
    message: "",
    notice: "",
    autoApprovePrefix: "",
    updatedAt: EPOCH,
  };
  const seed = env.DEFAULT_AUTH_TOKEN?.trim();
  return seed ? { ...base, authToken: seed } : base;
}

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

// Reject patches with errors. null or empty authToken clears the override.
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
    if (isInt(p.minSupportedVersionCode)) next.minSupportedVersionCode = p.minSupportedVersionCode;
    else errors.push("minSupportedVersionCode must be a non-negative integer");
  }
  if ("latestVersionCode" in p) {
    if (isInt(p.latestVersionCode)) next.latestVersionCode = p.latestVersionCode;
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
  if ("notice" in p) {
    if (typeof p.notice === "string") next.notice = p.notice;
    else errors.push("notice must be a string");
  }
  if ("autoApprovePrefix" in p) {
    if (typeof p.autoApprovePrefix === "string") next.autoApprovePrefix = p.autoApprovePrefix;
    else errors.push("autoApprovePrefix must be a string");
  }

  if (errors.length === 0) next.updatedAt = new Date().toISOString();
  return { next, errors };
}

// FNV-1a cache tag; not a cryptographic hash.
export function weakEtag(body: string): string {
  let hash = 0x811c9dc5;
  for (let i = 0; i < body.length; i++) {
    hash ^= body.charCodeAt(i);
    hash = Math.imul(hash, 0x01000193);
  }
  return `W/"${(hash >>> 0).toString(16)}"`;
}
