// In-memory stand-ins for the Cloudflare bindings the Worker uses, matching the exact statements our code
// issues. Shared by the unit tests (users state machine) and the router integration tests.

import { UserRow } from "../src/users";

export class FakeD1 {
  rows = new Map<string, UserRow>();
  metrics = new Map<string, number>();

  prepare(sql: string) {
    return new FakeStatement(this, sql);
  }

  exec(sql: string, args: unknown[]): { first: unknown; all: unknown[] } {
    if (sql.startsWith("SELECT name, value FROM metrics")) {
      const all = [...this.metrics.entries()].sort().map(([name, value]) => ({ name, value }));
      return { first: null, all };
    }
    if (sql.startsWith("SELECT") && sql.includes("WHERE admno = ?")) {
      return { first: this.rows.get(String(args[0])) ?? null, all: [] };
    }
    if (sql.startsWith("SELECT") && sql.includes("ORDER BY")) {
      const all = [...this.rows.values()].sort((a, b) => {
        const ap = a.status === "pending" ? 1 : 0;
        const bp = b.status === "pending" ? 1 : 0;
        if (ap !== bp) return bp - ap;
        return a.last_seen_at < b.last_seen_at ? 1 : -1;
      });
      return { first: null, all };
    }
    if (sql.startsWith("INSERT INTO metrics")) {
      const [name, count] = args as [string, number];
      this.metrics.set(name, (this.metrics.get(name) ?? 0) + Number(count));
      return { first: null, all: [] };
    }
    if (sql.startsWith("INSERT INTO users")) {
      const [admno, name, email, status, role, createdAt, lastSeen, firstSeen, approvedAt, avn, avc, dm, sdk] =
        args as unknown[];
      this.rows.set(String(admno), {
        admno: String(admno),
        name: String(name),
        email: String(email),
        status: status as UserRow["status"],
        role: role as UserRow["role"],
        created_at: String(createdAt),
        last_seen_at: String(lastSeen),
        first_seen_at: String(firstSeen),
        approved_at: String(approvedAt),
        app_version_name: String(avn),
        app_version_code: Number(avc),
        device_model: String(dm),
        android_sdk: Number(sdk),
        session_count: 1,
      });
      return { first: null, all: [] };
    }
    if (sql.startsWith("UPDATE users SET name")) {
      const [name, email, role, status, lastSeen, firstSeen, approvedAt, avn, avc, dm, sdk, count, admno] =
        args as unknown[];
      const row = this.rows.get(String(admno));
      if (row) {
        this.rows.set(String(admno), {
          ...row,
          name: String(name),
          email: String(email),
          role: role as UserRow["role"],
          status: status as UserRow["status"],
          last_seen_at: String(lastSeen),
          first_seen_at: String(firstSeen),
          approved_at: String(approvedAt),
          app_version_name: String(avn),
          app_version_code: Number(avc),
          device_model: String(dm),
          android_sdk: Number(sdk),
          session_count: Number(count),
        });
      }
      return { first: null, all: [] };
    }
    if (sql.startsWith("UPDATE users SET status")) {
      const [status, approvedAt, admno] = args as string[];
      const row = this.rows.get(admno);
      if (row) this.rows.set(admno, { ...row, status: status as UserRow["status"], approved_at: String(approvedAt) });
      return { first: null, all: [] };
    }
    throw new Error(`unhandled sql: ${sql}`);
  }
}

class FakeStatement {
  constructor(
    private db: FakeD1,
    private sql: string,
    private args: unknown[] = [],
  ) {}

  bind(...values: unknown[]) {
    return new FakeStatement(this.db, this.sql, values);
  }

  async first<T>(): Promise<T | null> {
    return this.db.exec(this.sql, this.args).first as T | null;
  }

  async all<T>(): Promise<{ results: T[] }> {
    return { results: this.db.exec(this.sql, this.args).all as T[] };
  }

  async run(): Promise<void> {
    this.db.exec(this.sql, this.args);
  }
}

/** Minimal KV: only get/put strings, which is all the config store needs. */
export class FakeKV {
  store = new Map<string, string>();
  async get(key: string): Promise<string | null> {
    return this.store.get(key) ?? null;
  }
  async put(key: string, value: string): Promise<void> {
    this.store.set(key, value);
  }
}

/** Build a fake iCloudEMS access token (unsigned; our decode never verifies the signature). */
export function fakeIcloudToken(admno: string, name = `Name ${admno}`): string {
  const b64 = (o: unknown) =>
    btoa(JSON.stringify(o)).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
  return `${b64({ alg: "HS256" })}.${b64({ admno, name, email: `${admno}@gu.edu`, exp: 9_999_999_999 })}.sig`;
}
