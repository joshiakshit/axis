import { describe, expect, it } from "vitest";
import { Env } from "../src/config";
import { IcloudClaims } from "../src/session";
import { UserRow, listUsers, setStatus, upsertOnSession } from "../src/users";

// Minimal in-memory stand-in for the exact D1 statements users.ts issues, so the governance state machine
// can be tested without a live database.
class FakeD1 {
  rows = new Map<string, UserRow>();

  prepare(sql: string) {
    return new FakeStatement(this, sql);
  }

  exec(sql: string, args: unknown[]): { first: unknown; all: unknown[] } {
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
    if (sql.startsWith("INSERT INTO users")) {
      const [admno, name, email, status, role, created_at, last_seen_at] = args as string[];
      this.rows.set(admno, {
        admno,
        name,
        email,
        status: status as UserRow["status"],
        role: role as UserRow["role"],
        created_at,
        last_seen_at,
      });
      return { first: null, all: [] };
    }
    if (sql.startsWith("UPDATE users SET name")) {
      const [name, email, role, status, last_seen_at, admno] = args as string[];
      const row = this.rows.get(admno);
      if (row) {
        this.rows.set(admno, {
          ...row,
          name,
          email,
          role: role as UserRow["role"],
          status: status as UserRow["status"],
          last_seen_at,
        });
      }
      return { first: null, all: [] };
    }
    if (sql.startsWith("UPDATE users SET status")) {
      const [status, admno] = args as string[];
      const row = this.rows.get(admno);
      if (row) this.rows.set(admno, { ...row, status: status as UserRow["status"] });
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

function env(): Env {
  return { DB: new FakeD1() } as unknown as Env;
}

function claims(admno: string): IcloudClaims {
  return { admno, name: `Name ${admno}`, email: `${admno}@gu.edu`, exp: 9_999_999_999 };
}

describe("upsertOnSession", () => {
  it("creates a new non-admin user as pending", async () => {
    const e = env();
    const user = await upsertOnSession(e, claims("21001"), false, false);
    expect(user).toMatchObject({ status: "pending", role: "user" });
  });

  it("creates an owner (allowlisted) as approved admin", async () => {
    const user = await upsertOnSession(env(), claims("21000"), true, false);
    expect(user).toMatchObject({ status: "approved", role: "admin" });
  });

  it("auto-approves a new user when open enrollment is on", async () => {
    const user = await upsertOnSession(env(), claims("21002"), false, true);
    expect(user).toMatchObject({ status: "approved", role: "user" });
  });

  it("forces an existing user to admin+approved when they become an owner", async () => {
    const e = env();
    await upsertOnSession(e, claims("21003"), false, false); // pending user
    const user = await upsertOnSession(e, claims("21003"), true, false);
    expect(user).toMatchObject({ status: "approved", role: "admin" });
  });

  it("refreshes last_seen without changing status on a repeat session", async () => {
    const e = env();
    const first = await upsertOnSession(e, claims("21004"), false, false);
    const second = await upsertOnSession(e, claims("21004"), false, false);
    expect(second.status).toBe("pending");
    expect(second.last_seen_at >= first.last_seen_at).toBe(true);
  });
});

describe("setStatus and listUsers", () => {
  it("allow then kick flips status and returns null for unknown admno", async () => {
    const e = env();
    await upsertOnSession(e, claims("21005"), false, false);
    expect((await setStatus(e, "21005", "approved"))?.status).toBe("approved");
    expect((await setStatus(e, "21005", "pending"))?.status).toBe("pending");
    expect(await setStatus(e, "does-not-exist", "approved")).toBeNull();
  });

  it("lists pending users first", async () => {
    const e = env();
    await upsertOnSession(e, claims("21006"), true, false); // approved admin
    await upsertOnSession(e, claims("21007"), false, false); // pending
    const users = await listUsers(e);
    expect(users[0].status).toBe("pending");
  });
});
