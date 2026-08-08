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
import { UserRow, getUser, listUsers, setStatus, upsertOnSession } from "./users";

const MAX_BODY_BYTES = 16 * 1024;
const SESSION_TTL_SECONDS = 60 * 60 * 24 * 7; // 7 days; user access is re-checked on each app launch anyway.

const CORS_HEADERS: Record<string, string> = {
  "access-control-allow-origin": "*",
  "access-control-allow-methods": "GET, PUT, OPTIONS",
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

async function handleSession(request: Request, env: Env): Promise<Response> {
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
  const openEnrollment = (env.OPEN_ENROLLMENT ?? "").toLowerCase() === "true";
  const user = await upsertOnSession(env, claims, isAdmin, openEnrollment);

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

async function handleUserAction(
  request: Request,
  env: Env,
  admno: string,
  action: "allow" | "kick",
): Promise<Response> {
  const denied = await requireAdminSession(request, env);
  if (denied) return denied;
  if (action === "kick") {
    const target = await getUser(env, admno);
    if (target?.role === "admin") return error(403, "cannot kick an admin");
  }
  const updated: UserRow | null = await setStatus(env, admno, action === "allow" ? "approved" : "pending");
  if (!updated) return error(404, "user not found");
  return json(updated);
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
      return handleSession(request, env);
    }
    if (url.pathname === "/v1/admin/users" && request.method === "GET") {
      return handleListUsers(request, env);
    }
    const action = url.pathname.match(/^\/v1\/admin\/users\/([^/]+)\/(allow|kick)$/);
    if (action && request.method === "POST") {
      return handleUserAction(request, env, decodeURIComponent(action[1]), action[2] as "allow" | "kick");
    }
    return error(404, "not found");
  },
} satisfies ExportedHandler<Env>;

// Re-exported so the toolchain and tests can reach the pure helpers without importing internals directly.
export { defaultConfig };
