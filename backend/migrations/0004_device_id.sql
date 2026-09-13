-- College-compatible device ID reported by the latest Axis session.
ALTER TABLE users ADD COLUMN device_id TEXT NOT NULL DEFAULT '';
