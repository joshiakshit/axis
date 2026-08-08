// D1-backed user registry. One row per admno; the governance state machine lives here.

import { Env } from "./config";
import { IcloudClaims, UserRole } from "./session";

export type UserStatus = "pending" | "approved";

export interface UserRow {
  admno: string;
  name: string;
  email: string;
  status: UserStatus;
  role: UserRole;
  created_at: string;
  last_seen_at: string;
}

const SELECT = "SELECT admno, name, email, status, role, created_at, last_seen_at FROM users";

export async function getUser(env: Env, admno: string): Promise<UserRow | null> {
  return env.DB.prepare(`${SELECT} WHERE admno = ?`).bind(admno).first<UserRow>();
}

export async function listUsers(env: Env): Promise<UserRow[]> {
  // Pending first (so onboarding requests surface at the top), then most-recently-seen.
  const res = await env.DB.prepare(`${SELECT} ORDER BY (status = 'pending') DESC, last_seen_at DESC`).all<UserRow>();
  return res.results ?? [];
}

/**
 * Record a session for `claims`: create the row on first sight (pending, unless the caller is an owner or
 * open enrollment is on), refresh name/email + last_seen otherwise. Owner admnos are always forced to
 * admin + approved so the owner can never lock themselves out.
 */
export async function upsertOnSession(
  env: Env,
  claims: IcloudClaims,
  isAdmin: boolean,
  openEnrollment: boolean,
): Promise<UserRow> {
  const now = new Date().toISOString();
  const existing = await getUser(env, claims.admno);

  if (!existing) {
    const status: UserStatus = isAdmin || openEnrollment ? "approved" : "pending";
    const role: UserRole = isAdmin ? "admin" : "user";
    await env.DB.prepare(
      "INSERT INTO users (admno, name, email, status, role, created_at, last_seen_at) VALUES (?, ?, ?, ?, ?, ?, ?)",
    )
      .bind(claims.admno, claims.name, claims.email, status, role, now, now)
      .run();
    return { admno: claims.admno, name: claims.name, email: claims.email, status, role, created_at: now, last_seen_at: now };
  }

  const role: UserRole = isAdmin ? "admin" : existing.role;
  const status: UserStatus = isAdmin ? "approved" : existing.status;
  const name = claims.name || existing.name;
  const email = claims.email || existing.email;
  await env.DB.prepare("UPDATE users SET name = ?, email = ?, role = ?, status = ?, last_seen_at = ? WHERE admno = ?")
    .bind(name, email, role, status, now, claims.admno)
    .run();
  return { ...existing, name, email, role, status, last_seen_at: now };
}

export async function setStatus(env: Env, admno: string, status: UserStatus): Promise<UserRow | null> {
  const existing = await getUser(env, admno);
  if (!existing) return null;
  await env.DB.prepare("UPDATE users SET status = ? WHERE admno = ?").bind(status, admno).run();
  return { ...existing, status };
}
