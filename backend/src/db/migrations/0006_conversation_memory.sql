CREATE TABLE IF NOT EXISTS conversation_memories (
  conversation_id TEXT PRIMARY KEY,
  short_term TEXT NOT NULL DEFAULT '',
  long_term TEXT NOT NULL DEFAULT '',
  last_consolidated_position INTEGER NOT NULL DEFAULT -1,
  revision INTEGER NOT NULL DEFAULT 0,
  updated_at INTEGER NOT NULL,
  FOREIGN KEY (conversation_id) REFERENCES conversations(id) ON DELETE CASCADE
);
