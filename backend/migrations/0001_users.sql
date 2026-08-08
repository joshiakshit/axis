-- Axis governed users. One row per iCloudEMS admno that has ever opened a governed build.
-- Apply with:  npx wrangler d1 migrations apply axis   (add --remote for the deployed DB)
CREATE TABLE IF NOT EXISTS users (
  admno        TEXT PRIMARY KEY,
  name         TEXT NOT NULL DEFAULT '',
  email        TEXT NOT NULL DEFAULT '',
  status       TEXT NOT NULL DEFAULT 'pending',  -- 'pending' | 'approved'
  role         TEXT NOT NULL DEFAULT 'user',     -- 'user' | 'admin'
  created_at   TEXT NOT NULL,                     -- ISO-8601
  last_seen_at TEXT NOT NULL                      -- ISO-8601
);

CREATE INDEX IF NOT EXISTS idx_users_status ON users (status);
