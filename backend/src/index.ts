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

const MAX_BODY_BYTES = 16 * 1024;

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
    return error(404, "not found");
  },
} satisfies ExportedHandler<Env>;

// Re-exported so the toolchain and tests can reach the pure helpers without importing internals directly.
export { defaultConfig };
