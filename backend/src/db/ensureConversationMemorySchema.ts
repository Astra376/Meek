import type { Env } from "../env";
import { run } from "./client";

const readyByDatabase = new WeakMap<D1Database, Promise<void>>();

async function ensureConversationMemorySchemaImpl(env: Env): Promise<void> {
  await run(
    env.DB.prepare(
      `
      CREATE TABLE IF NOT EXISTS conversation_memories (
        conversation_id TEXT PRIMARY KEY,
        short_term TEXT NOT NULL DEFAULT '',
        long_term TEXT NOT NULL DEFAULT '',
        last_consolidated_position INTEGER NOT NULL DEFAULT -1,
        revision INTEGER NOT NULL DEFAULT 0,
        updated_at INTEGER NOT NULL,
        FOREIGN KEY (conversation_id) REFERENCES conversations(id) ON DELETE CASCADE
      )
      `
    )
  );
}

export function ensureConversationMemorySchema(env: Env): Promise<void> {
  const existing = readyByDatabase.get(env.DB);
  if (existing) return existing;

  const pending = ensureConversationMemorySchemaImpl(env).catch((error) => {
    readyByDatabase.delete(env.DB);
    throw error;
  });
  readyByDatabase.set(env.DB, pending);
  return pending;
}
