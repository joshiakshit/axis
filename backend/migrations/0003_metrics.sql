-- Retention timestamps + a global usage-counter table for the Admin dashboard.
-- Apply with:  npx wrangler d1 migrations apply axis   (add --remote for the deployed DB)
ALTER TABLE users ADD COLUMN first_seen_at TEXT NOT NULL DEFAULT '';
ALTER TABLE users ADD COLUMN approved_at TEXT NOT NULL DEFAULT '';

-- Aggregate, privacy-safe event counters: QR scans, exports, session failures, etc. One row per event name.
CREATE TABLE IF NOT EXISTS metrics (
  name  TEXT PRIMARY KEY,
  value INTEGER NOT NULL DEFAULT 0
);
