ALTER TABLE conversations ADD COLUMN unread_count INTEGER NOT NULL DEFAULT 0;
ALTER TABLE conversations ADD COLUMN has_unread_badge INTEGER NOT NULL DEFAULT 0;
ALTER TABLE users ADD COLUMN last_email_sent_at INTEGER;
