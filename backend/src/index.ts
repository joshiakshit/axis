// Axis backend — v1: remote config.
//
// Routes:
//   GET  /healthz         → liveness probe
//   GET  /v1/config       → the RemoteConfig for a tenant (ETag + conditional GET). Soft-gated by
//                           `x-axis-key` only when APP_ACCESS_KEY is set.
//   PUT  /v1/config       → merge a partial config and persist it. Requires `Authorization: Bearer <ADMIN_TOKEN>`.
//
// Config is stored one KV record per tenant under `config:<tenant>`; `?tenant=` selects it (defaults to
// DEFAULT_TENANT / "gu"). Everything else 404s.

import { Env, RemoteConfig, applyPatch, defaultConfig, parseStored, weakEtag } from "./config";
import { decodeIcloudToken, signSession, verifySession } from "./session";
import {
  SessionMeta,
  UserRow,
  UserStatus,
  approveAllPending,
  bumpMetrics,
  getMetrics,
  getUser,
  listUsers,
  setStatus,
  upsertOnSession,
} from "./users";

const MAX_BODY_BYTES = 16 * 1024;
const MAX_APK_BYTES = 150 * 1024 * 1024; // room for a fat universal APK
const APK_KEY = "release/latest.apk";
const SESSION_TTL_SECONDS = 60 * 60 * 24 * 7; // 7 days; user access is re-checked on each app launch anyway.

const CORS_HEADERS: Record<string, string> = {
  "access-control-allow-origin": "*",
  "access-control-allow-methods": "GET, PUT, POST, OPTIONS",
  "access-control-allow-headers": "authorization, content-type, x-axis-key, if-none-match",
  "access-control-max-age": "86400",
};

function json(body: unknown, init: ResponseInit = {}): Response {
  const headers = new Headers(init.headers);
  headers.set("content-type", "application/json; charset=utf-8");
  for (const [k, v] of Object.entries(CORS_HEADERS)) headers.set(k, v);
  return new Response(JSON.stringify(body), { ...init, headers });
}

function error(status: number, message: string): Response {
  return json({ error: message }, { status });
}

/** Length-independent constant-time string compare, to keep the admin token check off timing oracles. */
function timingSafeEqual(a: string, b: string): boolean {
  const enc = new TextEncoder();
  const ab = enc.encode(a);
  const bb = enc.encode(b);
  // Compare against a fixed-length buffer so length differences don't short-circuit.
  const len = Math.max(ab.length, bb.length);
  let diff = ab.length ^ bb.length;
  for (let i = 0; i < len; i++) diff |= (ab[i] ?? 0) ^ (bb[i] ?? 0);
  return diff === 0;
}

function tenantKey(url: URL, env: Env): string {
  const tenant = (url.searchParams.get("tenant") || env.DEFAULT_TENANT || "gu")
    .toLowerCase()
    .replace(/[^a-z0-9_-]/g, "")
    .slice(0, 32);
  return `config:${tenant || "gu"}`;
}

async function readConfig(env: Env, key: string): Promise<RemoteConfig> {
  const raw = await env.CONFIG.get(key);
  return parseStored(raw, env);
}

function requireAppKey(request: Request, env: Env): Response | null {
  if (!env.APP_ACCESS_KEY) return null; // gate disabled
  const provided = request.headers.get("x-axis-key") ?? "";
  return timingSafeEqual(provided, env.APP_ACCESS_KEY) ? null : error(401, "invalid or missing x-axis-key");
}

function requireAdmin(request: Request, env: Env): Response | null {
  if (!env.ADMIN_TOKEN) return error(503, "writes are disabled: ADMIN_TOKEN is not configured");
  const header = request.headers.get("authorization") ?? "";
  const token = header.toLowerCase().startsWith("bearer ") ? header.slice(7).trim() : "";
  return token && timingSafeEqual(token, env.ADMIN_TOKEN) ? null : error(401, "invalid admin credentials");
}

async function handleGetConfig(request: Request, env: Env, url: URL): Promise<Response> {
  const gate = requireAppKey(request, env);
  if (gate) return gate;

  const config = await readConfig(env, tenantKey(url, env));
  const body = JSON.stringify(config);
  const etag = weakEtag(body);

  if (request.headers.get("if-none-match") === etag) {
    return new Response(null, {
      status: 304,
      headers: { etag, "cache-control": "public, max-age=300", ...CORS_HEADERS },
    });
  }
  return new Response(body, {
    status: 200,
    headers: {
      "content-type": "application/json; charset=utf-8",
      etag,
      "cache-control": "public, max-age=300",
      ...CORS_HEADERS,
    },
  });
}

async function handlePutConfig(request: Request, env: Env, url: URL): Promise<Response> {
  const denied = requireAdmin(request, env);
  if (denied) return denied;

  const buf = await request.arrayBuffer();
  if (buf.byteLength > MAX_BODY_BYTES) return error(413, "config body too large");

  let patch: unknown;
  try {
    patch = JSON.parse(new TextDecoder().decode(buf) || "{}");
  } catch {
    return error(400, "body must be valid JSON");
  }

  const key = tenantKey(url, env);
  const current = await readConfig(env, key);
  const { next, errors } = applyPatch(current, patch);
  if (errors.length > 0) return json({ error: "validation failed", details: errors }, { status: 400 });

  await env.CONFIG.put(key, JSON.stringify(next));
  return json(next, { status: 200 });
}

// ---- User governance (Axis sessions + admin) ---------------------------------------------------------

/** Pull the optional per-launch telemetry out of a session body (all fields best-effort). */
function parseMeta(body: unknown): SessionMeta {
  const b = (body ?? {}) as Record<string, unknown>;
  const str = (v: unknown): string => (typeof v === "string" ? v.slice(0, 120) : "");
  const num = (v: unknown): number => (typeof v === "number" && Number.isFinite(v) ? Math.trunc(v) : 0);
  return {
    appVersionName: str(b.appVersionName),
    appVersionCode: num(b.appVersionCode),
    deviceModel: str(b.deviceModel),
    androidSdk: num(b.androidSdk),
  };
}

/** True when `admno` starts with any of the comma-separated prefixes (auto-approval rule). */
function matchesAutoApprove(admno: string, prefixes: string): boolean {
  return prefixes
    .split(",")
    .map((s) => s.trim())
    .filter(Boolean)
    .some((p) => admno.startsWith(p));
}

async function handleSession(request: Request, env: Env, url: URL): Promise<Response> {
  if (!env.SESSION_SECRET) return error(503, "sessions are disabled: SESSION_SECRET is not configured");

  const buf = await request.arrayBuffer();
  if (buf.byteLength > MAX_BODY_BYTES) return error(413, "body too large");
  let body: unknown;
  try {
    body = JSON.parse(new TextDecoder().decode(buf) || "{}");
  } catch {
    return error(400, "body must be valid JSON");
  }
  const raw = (body as { token?: unknown })?.token;
  const claims = typeof raw === "string" ? decodeIcloudToken(raw) : null;
  // Light validation: we only need the `admno` claim to identify the user. We deliberately do NOT reject on
  // token expiry — the app may hold a still-usable-but-expired access token, and an expired token still
  // proves which admno once authenticated. (Signature isn't verified either; hardenable later.)
  if (!claims) return error(401, "invalid iCloudEMS token");

  const admins = (env.ADMIN_ADMNOS ?? "").split(",").map((s) => s.trim()).filter(Boolean);
  const isAdmin = admins.includes(claims.admno);
  const config = await readConfig(env, tenantKey(url, env));
  const openEnrollment = (env.OPEN_ENROLLMENT ?? "").toLowerCase() === "true";
  const autoApprove = openEnrollment || matchesAutoApprove(claims.admno, config.autoApprovePrefix);
  const user = await upsertOnSession(env, claims, isAdmin, autoApprove, parseMeta(body));

  const sessionToken =
    user.status === "approved"
      ? await signSession(user.admno, user.role, env.SESSION_SECRET, SESSION_TTL_SECONDS)
      : undefined;
  return json({ status: user.status, role: user.role, admno: user.admno, name: user.name, sessionToken });
}

async function requireAdminSession(request: Request, env: Env): Promise<Response | null> {
  if (!env.SESSION_SECRET) return error(503, "sessions are disabled: SESSION_SECRET is not configured");
  const header = request.headers.get("authorization") ?? "";
  const token = header.toLowerCase().startsWith("bearer ") ? header.slice(7).trim() : "";
  const claims = token ? await verifySession(token, env.SESSION_SECRET) : null;
  if (!claims || claims.role !== "admin") return error(401, "admin session required");
  // Defense in depth: the caller must still be an admin in the store.
  const user = await getUser(env, claims.admno);
  if (!user || user.role !== "admin") return error(403, "not an admin");
  return null;
}

async function handleListUsers(request: Request, env: Env): Promise<Response> {
  const denied = await requireAdminSession(request, env);
  if (denied) return denied;
  return json({ users: await listUsers(env) });
}

type UserAction = "allow" | "kick" | "ban";

async function handleUserAction(request: Request, env: Env, admno: string, action: UserAction): Promise<Response> {
  const denied = await requireAdminSession(request, env);
  if (denied) return denied;
  if (action !== "allow") {
    // Admins can't be kicked or banned.
    const target = await getUser(env, admno);
    if (target?.role === "admin") return error(403, `cannot ${action} an admin`);
  }
  const status: UserStatus = action === "allow" ? "approved" : action === "ban" ? "banned" : "pending";
  const updated: UserRow | null = await setStatus(env, admno, status);
  if (!updated) return error(404, "user not found");
  return json(updated);
}

async function handleApproveAll(request: Request, env: Env): Promise<Response> {
  const denied = await requireAdminSession(request, env);
  if (denied) return denied;
  return json({ approved: await approveAllPending(env) });
}

async function handleHealth(request: Request, env: Env): Promise<Response> {
  const denied = await requireAdminSession(request, env);
  if (denied) return denied;
  const users = await listUsers(env);
  const by = (s: UserStatus) => users.filter((u) => u.status === s).length;
  const apkUploaded = env.APK ? (await env.APK.head(APK_KEY)) !== null : false;
  return json({
    service: "axis-backend",
    users: users.length,
    pending: by("pending"),
    approved: by("approved"),
    banned: by("banned"),
    apkUploaded,
    metrics: await getMetrics(env),
  });
}

/** Sanitize a client-reported usage batch: alnum/underscore names, bounded counts, capped length. */
function parseEvents(body: unknown): Array<{ name: string; count: number }> {
  const raw = (body as { events?: unknown })?.events;
  if (!Array.isArray(raw)) return [];
  const out: Array<{ name: string; count: number }> = [];
  for (const e of raw.slice(0, 50)) {
    const name = String((e as { name?: unknown })?.name ?? "")
      .toLowerCase()
      .replace(/[^a-z0-9_]/g, "")
      .slice(0, 40);
    if (!name) continue;
    const c = Number((e as { count?: unknown })?.count ?? 1);
    out.push({ name, count: Number.isFinite(c) ? Math.min(Math.max(Math.trunc(c), 1), 1000) : 1 });
  }
  return out;
}

async function handleEvents(request: Request, env: Env): Promise<Response> {
  if (!env.SESSION_SECRET) return error(503, "sessions are disabled: SESSION_SECRET is not configured");
  // Any valid Axis session (user or admin) may report its own usage.
  const header = request.headers.get("authorization") ?? "";
  const token = header.toLowerCase().startsWith("bearer ") ? header.slice(7).trim() : "";
  const claims = token ? await verifySession(token, env.SESSION_SECRET) : null;
  if (!claims) return error(401, "session required");

  const buf = await request.arrayBuffer();
  if (buf.byteLength > MAX_BODY_BYTES) return error(413, "body too large");
  let body: unknown;
  try {
    body = JSON.parse(new TextDecoder().decode(buf) || "{}");
  } catch {
    return error(400, "body must be valid JSON");
  }
  const events = parseEvents(body);
  if (events.length > 0) await bumpMetrics(env, events);
  return json({ ok: true, counted: events.length });
}

// ---- Admin config (remote config edited from the app's Admin page) ------------------------------------
//
// The app admin sets the force-update floor, the "latest build" fields, kill-switch, etc. from their phone.
// These reuse the same KV-backed RemoteConfig as /v1/config, but are gated by an *admin session* (role=admin)
// rather than the deploy-time ADMIN_TOKEN.

async function handleAdminGetConfig(request: Request, env: Env, url: URL): Promise<Response> {
  const denied = await requireAdminSession(request, env);
  if (denied) return denied;
  return json(await readConfig(env, tenantKey(url, env)));
}

async function handleAdminPutConfig(request: Request, env: Env, url: URL): Promise<Response> {
  const denied = await requireAdminSession(request, env);
  if (denied) return denied;

  const buf = await request.arrayBuffer();
  if (buf.byteLength > MAX_BODY_BYTES) return error(413, "config body too large");
  let patch: unknown;
  try {
    patch = JSON.parse(new TextDecoder().decode(buf) || "{}");
  } catch {
    return error(400, "body must be valid JSON");
  }
  const key = tenantKey(url, env);
  const { next, errors } = applyPatch(await readConfig(env, key), patch);
  if (errors.length > 0) return json({ error: "validation failed", details: errors }, { status: 400 });
  await env.CONFIG.put(key, JSON.stringify(next));
  return json(next, { status: 200 });
}

// ---- APK hosting (optional, self-hosted one-tap updates) ---------------------------------------------
//
// Turnkey delivery on the same Worker: the release APK lives in an R2 bucket bound as `APK`. GET streams it
// to the in-app updater; PUT (admin session or ADMIN_TOKEN, for CI) replaces it. Inert until R2 is bound —
// every route 503s so the Worker still deploys without a bucket, and any external URL works instead.

/** Admin gate that also accepts the deploy-time ADMIN_TOKEN, so CI can upload without an iCloudEMS login. */
async function requireAdminAny(request: Request, env: Env): Promise<Response | null> {
  const header = request.headers.get("authorization") ?? "";
  const token = header.toLowerCase().startsWith("bearer ") ? header.slice(7).trim() : "";
  if (env.ADMIN_TOKEN && token && timingSafeEqual(token, env.ADMIN_TOKEN)) return null;
  return requireAdminSession(request, env);
}

async function handleGetApk(request: Request, env: Env): Promise<Response> {
  if (!env.APK) return error(503, "apk hosting is disabled: no R2 bucket bound");
  const gate = requireAppKey(request, env);
  if (gate) return gate;
  const object = await env.APK.get(APK_KEY);
  if (!object) return error(404, "no APK uploaded yet");
  const meta = object.customMetadata ?? {};
  return new Response(object.body, {
    status: 200,
    headers: {
      "content-type": "application/vnd.android.package-archive",
      "content-disposition": 'attachment; filename="axis.apk"',
      "content-length": String(object.size),
      etag: object.httpEtag,
      "x-axis-version-code": meta.versionCode ?? "",
      "x-axis-version-name": meta.versionName ?? "",
      "cache-control": "no-cache",
      ...CORS_HEADERS,
    },
  });
}

async function handlePutApk(request: Request, env: Env, url: URL): Promise<Response> {
  if (!env.APK) return error(503, "apk hosting is disabled: no R2 bucket bound");
  const denied = await requireAdminAny(request, env);
  if (denied) return denied;
  const size = Number(request.headers.get("content-length") ?? "0");
  if (size > MAX_APK_BYTES) return error(413, "apk too large");
  if (!request.body) return error(400, "empty body");
  const versionCode = url.searchParams.get("versionCode") ?? "";
  const versionName = url.searchParams.get("versionName") ?? "";
  await env.APK.put(APK_KEY, request.body, {
    httpMetadata: { contentType: "application/vnd.android.package-archive" },
    customMetadata: { versionCode, versionName },
  });
  return json({ ok: true, versionCode, versionName });
}

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    const url = new URL(request.url);

    if (request.method === "OPTIONS") {
      return new Response(null, { status: 204, headers: CORS_HEADERS });
    }
    if (url.pathname === "/healthz" && request.method === "GET") {
      return json({ ok: true, service: "axis-backend", tenant: (env.DEFAULT_TENANT || "gu") });
    }
    if (url.pathname === "/v1/config") {
      if (request.method === "GET") return handleGetConfig(request, env, url);
      if (request.method === "PUT") return handlePutConfig(request, env, url);
      return error(405, "method not allowed");
    }
    if (url.pathname === "/v1/session" && request.method === "POST") {
      return handleSession(request, env, url);
    }
    if (url.pathname === "/v1/events" && request.method === "POST") {
      return handleEvents(request, env);
    }
    if (url.pathname === "/v1/admin/config") {
      if (request.method === "GET") return handleAdminGetConfig(request, env, url);
      if (request.method === "PUT") return handleAdminPutConfig(request, env, url);
      return error(405, "method not allowed");
    }
    if (url.pathname === "/v1/admin/users" && request.method === "GET") {
      return handleListUsers(request, env);
    }
    if (url.pathname === "/v1/admin/approve-all" && request.method === "POST") {
      return handleApproveAll(request, env);
    }
    if (url.pathname === "/v1/admin/health" && request.method === "GET") {
      return handleHealth(request, env);
    }
    if (url.pathname === "/v1/apk" && request.method === "GET") {
      return handleGetApk(request, env);
    }
    if (url.pathname === "/v1/admin/apk" && request.method === "PUT") {
      return handlePutApk(request, env, url);
    }
    const action = url.pathname.match(/^\/v1\/admin\/users\/([^/]+)\/(allow|kick|ban)$/);
    if (action && request.method === "POST") {
      return handleUserAction(request, env, decodeURIComponent(action[1]), action[2] as UserAction);
    }
    return error(404, "not found");
  },
} satisfies ExportedHandler<Env>;

// Re-exported so the toolchain and tests can reach the pure helpers without importing internals directly.
export { defaultConfig };
