PRAGMA foreign_keys=off;

CREATE TABLE IF NOT EXISTS characters_new (
  id TEXT PRIMARY KEY,
  owner_user_id TEXT NOT NULL,
  name TEXT NOT NULL,
  tagline TEXT NOT NULL,
  description TEXT NOT NULL,
  system_prompt TEXT NOT NULL,
  visibility TEXT NOT NULL CHECK (visibility IN ('public', 'unlisted', 'private')),
  avatar_url TEXT,
  public_chat_count INTEGER NOT NULL DEFAULT 0,
  like_count INTEGER NOT NULL DEFAULT 0,
  last_active_at INTEGER NOT NULL,
  created_at INTEGER NOT NULL,
  updated_at INTEGER NOT NULL,
  FOREIGN KEY (owner_user_id) REFERENCES users(id) ON DELETE CASCADE
);

INSERT INTO characters_new (
  id, owner_user_id, name, tagline, description, system_prompt, visibility,
  avatar_url, public_chat_count, like_count, last_active_at, created_at, updated_at
)
SELECT
  id, owner_user_id, name, tagline, description, system_prompt, visibility,
  avatar_url, public_chat_count, like_count, last_active_at, created_at, updated_at
FROM characters;

DROP TABLE characters;
ALTER TABLE characters_new RENAME TO characters;

CREATE INDEX IF NOT EXISTS idx_characters_visibility_activity
  ON characters(visibility, last_active_at DESC, public_chat_count DESC, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_characters_owner_updated
  ON characters(owner_user_id, updated_at DESC);

PRAGMA foreign_keys=on;
