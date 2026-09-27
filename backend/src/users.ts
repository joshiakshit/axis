import { Env } from "./config";
import { IcloudClaims, UserRole } from "./session";

export type UserStatus = "pending" | "approved" | "banned";

export interface SessionMeta {
  appVersionName: string;
  appVersionCode: number;
  deviceModel: string;
  androidSdk: number;
}

export const EMPTY_META: SessionMeta = {
  appVersionName: "",
  appVersionCode: 0,
  deviceModel: "",
  androidSdk: 0,
};

export interface UserRow {
  admno: string;
  name: string;
  email: string;
  status: UserStatus;
  role: UserRole;
  created_at: string;
  last_seen_at: string;
  first_seen_at: string;
  approved_at: string;
  app_version_name: string;
  app_version_code: number;
  device_model: string;
  android_sdk: number;
  device_id: string;
  session_count: number;
}

const SELECT =
  "SELECT admno, name, email, status, role, created_at, last_seen_at, first_seen_at, approved_at, " +
  "app_version_name, app_version_code, device_model, android_sdk, device_id, session_count FROM users";

export async function getUser(env: Env, admno: string): Promise<UserRow | null> {
  return env.DB.prepare(`${SELECT} WHERE admno = ?`).bind(admno).first<UserRow>();
}

export async function listUsers(env: Env): Promise<UserRow[]> {
  const res = await env.DB.prepare(`${SELECT} ORDER BY (status = 'pending') DESC, last_seen_at DESC`).all<UserRow>();
  return res.results ?? [];
}

export async function upsertOnSession(
  env: Env,
  claims: IcloudClaims,
  isAdmin: boolean,
  autoApprove: boolean,
  meta: SessionMeta = EMPTY_META,
): Promise<UserRow> {
  const now = new Date().toISOString();
  const existing = await getUser(env, claims.admno);

  if (!existing) {
    const status: UserStatus = isAdmin || autoApprove ? "approved" : "pending";
    const role: UserRole = isAdmin ? "admin" : "user";
    const approvedAt = status === "approved" ? now : "";
    await env.DB.prepare(
      "INSERT INTO users (admno, name, email, status, role, created_at, last_seen_at, first_seen_at, approved_at, " +
        "app_version_name, app_version_code, device_model, android_sdk, session_count) " +
        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1)",
    )
      .bind(
        claims.admno,
        claims.name,
        claims.email,
        status,
        role,
        now,
        now,
        now,
        approvedAt,
        meta.appVersionName,
        meta.appVersionCode,
        meta.deviceModel,
        meta.androidSdk,
      )
      .run();
    return {
      admno: claims.admno,
      name: claims.name,
      email: claims.email,
      status,
      role,
      created_at: now,
      last_seen_at: now,
      first_seen_at: now,
      approved_at: approvedAt,
      app_version_name: meta.appVersionName,
      app_version_code: meta.appVersionCode,
      device_model: meta.deviceModel,
      android_sdk: meta.androidSdk,
      device_id: "",
      session_count: 1,
    };
  }

  const role: UserRole = isAdmin ? "admin" : existing.role;
  // Only owner status overrides a ban on login.
  const status: UserStatus = isAdmin ? "approved" : existing.status;
  const name = claims.name || existing.name;
  const email = claims.email || existing.email;
  const firstSeen = existing.first_seen_at || existing.created_at || now;
  const approvedAt = status === "approved" && !existing.approved_at ? now : existing.approved_at;
  // Older clients may omit telemetry; preserve stored values.
  const appVersionName = meta.appVersionName || existing.app_version_name;
  const appVersionCode = meta.appVersionCode || existing.app_version_code;
  const deviceModel = meta.deviceModel || existing.device_model;
  const androidSdk = meta.androidSdk || existing.android_sdk;
  const sessionCount = existing.session_count + 1;
  await env.DB.prepare(
    "UPDATE users SET name = ?, email = ?, role = ?, status = ?, last_seen_at = ?, first_seen_at = ?, approved_at = ?, " +
      "app_version_name = ?, app_version_code = ?, device_model = ?, android_sdk = ?, " +
      "session_count = ? WHERE admno = ?",
  )
    .bind(
      name,
      email,
      role,
      status,
      now,
      firstSeen,
      approvedAt,
      appVersionName,
      appVersionCode,
      deviceModel,
      androidSdk,
      sessionCount,
      claims.admno,
    )
    .run();
  return {
    ...existing,
    name,
    email,
    role,
    status,
    last_seen_at: now,
    first_seen_at: firstSeen,
    approved_at: approvedAt,
    app_version_name: appVersionName,
    app_version_code: appVersionCode,
    device_model: deviceModel,
    android_sdk: androidSdk,
    session_count: sessionCount,
  };
}

export async function setStatus(env: Env, admno: string, status: UserStatus): Promise<UserRow | null> {
  const existing = await getUser(env, admno);
  if (!existing) return null;
  const now = new Date().toISOString();
  const approvedAt = status === "approved" && !existing.approved_at ? now : existing.approved_at;
  await env.DB.prepare("UPDATE users SET status = ?, approved_at = ? WHERE admno = ?").bind(status, approvedAt, admno).run();
  return { ...existing, status, approved_at: approvedAt };
}

export async function approveAllPending(env: Env): Promise<number> {
  const pending = (await listUsers(env)).filter((u) => u.status === "pending");
  for (const u of pending) await setStatus(env, u.admno, "approved");
  return pending.length;
}

export async function bumpMetrics(env: Env, events: Array<{ name: string; count: number }>): Promise<void> {
  for (const e of events) {
    await env.DB.prepare(
      "INSERT INTO metrics (name, value) VALUES (?, ?) ON CONFLICT(name) DO UPDATE SET value = value + ?",
    )
      .bind(e.name, e.count, e.count)
      .run();
  }
}

export async function getMetrics(env: Env): Promise<Record<string, number>> {
  const res = await env.DB.prepare("SELECT name, value FROM metrics ORDER BY name").all<{ name: string; value: number }>();
  const out: Record<string, number> = {};
  for (const row of res.results ?? []) out[row.name] = row.value;
  return out;
}
