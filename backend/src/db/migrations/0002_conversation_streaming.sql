ALTER TABLE conversations ADD COLUMN version INTEGER NOT NULL DEFAULT 0;
ALTER TABLE conversations ADD COLUMN active_run_id TEXT;
ALTER TABLE conversations ADD COLUMN active_run_expires_at INTEGER;
