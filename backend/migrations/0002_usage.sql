-- Usage telemetry for the admin dashboard. Each column is written on every POST /v1/session, so the admin
-- can see who runs which build (drives the force-update decision), on what device, and how active they are.
-- Apply with:  npx wrangler d1 migrations apply axis   (add --remote for the deployed DB)
ALTER TABLE users ADD COLUMN app_version_name TEXT NOT NULL DEFAULT '';
ALTER TABLE users ADD COLUMN app_version_code INTEGER NOT NULL DEFAULT 0;
ALTER TABLE users ADD COLUMN device_model TEXT NOT NULL DEFAULT '';
ALTER TABLE users ADD COLUMN android_sdk INTEGER NOT NULL DEFAULT 0;
ALTER TABLE users ADD COLUMN session_count INTEGER NOT NULL DEFAULT 0;
