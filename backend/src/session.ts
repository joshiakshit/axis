// Axis session layer.
//
// Two jobs:
//   1. `decodeIcloudToken` — LIGHT validation of the iCloudEMS access token: read the `admno`/name/email out
//      of the JWT payload and check structure. We do NOT verify iCloudEMS's signature (their HS256 key is not
//      ours) — this is the accepted v1 trade-off, hardenable later with a server-side iCloudEMS probe.
//   2. `signSession`/`verifySession` — mint and check OUR OWN short-lived HS256 token (signed with
//      SESSION_SECRET) that carries `admno` + `role`. This is what authorizes the admin endpoints.

const enc = new TextEncoder();
const dec = new TextDecoder();

export type UserRole = "user" | "admin";

export interface IcloudClaims {
  admno: string;
  name: string;
  email: string;
  exp: number;
}

export interface SessionClaims {
  admno: string;
  role: UserRole;
  iat: number;
  exp: number;
}

function b64urlEncodeBytes(bytes: Uint8Array): string {
  let bin = "";
  for (let i = 0; i < bytes.length; i++) bin += String.fromCharCode(bytes[i]);
  return btoa(bin).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

function b64urlDecodeBytes(value: string): Uint8Array {
  const norm = value.replace(/-/g, "+").replace(/_/g, "/");
  const pad = norm.length % 4 === 0 ? "" : "=".repeat(4 - (norm.length % 4));
  const bin = atob(norm + pad);
  const out = new Uint8Array(bin.length);
  for (let i = 0; i < bin.length; i++) out[i] = bin.charCodeAt(i);
  return out;
}

function b64urlEncodeString(value: string): string {
  return b64urlEncodeBytes(enc.encode(value));
}

function b64urlDecodeString(value: string): string {
  return dec.decode(b64urlDecodeBytes(value));
}

/** Read the identity claims from an iCloudEMS access token without verifying its signature. */
export function decodeIcloudToken(token: string): IcloudClaims | null {
  const parts = token.split(".");
  if (parts.length < 2) return null;
  try {
    const payload = JSON.parse(b64urlDecodeString(parts[1])) as Record<string, unknown>;
    const admnoRaw = (payload.admno as string) || (payload.preferred_username as string) || "";
    const admno = String(admnoRaw).trim();
    if (!admno) return null;
    return {
      admno,
      name: String(payload.name ?? ""),
      email: String(payload.email ?? ""),
      exp: typeof payload.exp === "number" ? payload.exp : 0,
    };
  } catch {
    return null;
  }
}

async function hmacKey(secret: string): Promise<CryptoKey> {
  return crypto.subtle.importKey("raw", enc.encode(secret), { name: "HMAC", hash: "SHA-256" }, false, [
    "sign",
    "verify",
  ]);
}

/** Sign an Axis session token (HS256) carrying admno + role, valid for `ttlSeconds`. */
export async function signSession(
  admno: string,
  role: UserRole,
  secret: string,
  ttlSeconds: number,
): Promise<string> {
  const now = Math.floor(Date.now() / 1000);
  const header = b64urlEncodeString(JSON.stringify({ alg: "HS256", typ: "JWT" }));
  const payload = b64urlEncodeString(JSON.stringify({ admno, role, iat: now, exp: now + ttlSeconds }));
  const signingInput = `${header}.${payload}`;
  const key = await hmacKey(secret);
  const sig = new Uint8Array(await crypto.subtle.sign("HMAC", key, enc.encode(signingInput)));
  return `${signingInput}.${b64urlEncodeBytes(sig)}`;
}

/** Verify an Axis session token's signature and expiry; returns its claims or null. */
export async function verifySession(token: string, secret: string): Promise<SessionClaims | null> {
  const parts = token.split(".");
  if (parts.length !== 3) return null;
  const signingInput = `${parts[0]}.${parts[1]}`;
  const key = await hmacKey(secret);
  let ok = false;
  try {
    ok = await crypto.subtle.verify("HMAC", key, b64urlDecodeBytes(parts[2]), enc.encode(signingInput));
  } catch {
    return null;
  }
  if (!ok) return null;
  try {
    const payload = JSON.parse(b64urlDecodeString(parts[1])) as Record<string, unknown>;
    const admno = String(payload.admno ?? "");
    if (!admno) return null;
    const exp = typeof payload.exp === "number" ? payload.exp : 0;
    if (exp < Math.floor(Date.now() / 1000)) return null;
    return {
      admno,
      role: payload.role === "admin" ? "admin" : "user",
      iat: typeof payload.iat === "number" ? payload.iat : 0,
      exp,
    };
  } catch {
    return null;
  }
}
