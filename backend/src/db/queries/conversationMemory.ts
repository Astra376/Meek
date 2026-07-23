import type { Env } from "../../env";
import { first, run } from "../client";

export interface ConversationMemoryRecord {
  conversation_id: string;
  short_term: string;
  long_term: string;
  last_consolidated_position: number;
  revision: number;
  updated_at: number;
}

export async function getConversationMemory(
  env: Env,
  conversationId: string
): Promise<ConversationMemoryRecord | null> {
  return first<ConversationMemoryRecord>(
    env.DB.prepare(
      "SELECT * FROM conversation_memories WHERE conversation_id = ? LIMIT 1"
    ).bind(conversationId)
  );
}

export async function createConversationMemoryIfMissing(
  env: Env,
  conversationId: string,
  now: number
): Promise<void> {
  await run(
    env.DB.prepare(
      `
      INSERT OR IGNORE INTO conversation_memories (
        conversation_id, short_term, long_term, last_consolidated_position, revision, updated_at
      )
      VALUES (?, '', '', -1, 0, ?)
      `
    ).bind(conversationId, now)
  );
}

export async function saveConversationMemory(
  env: Env,
  input: {
    conversationId: string;
    shortTerm: string;
    longTerm: string;
    updatedAt: number;
  }
): Promise<void> {
  await run(
    env.DB.prepare(
      `
      INSERT INTO conversation_memories (
        conversation_id, short_term, long_term, last_consolidated_position, revision, updated_at
      )
      VALUES (?, ?, ?, -1, 1, ?)
      ON CONFLICT(conversation_id) DO UPDATE SET
        short_term = excluded.short_term,
        long_term = excluded.long_term,
        revision = conversation_memories.revision + 1,
        updated_at = excluded.updated_at
      `
    ).bind(input.conversationId, input.shortTerm, input.longTerm, input.updatedAt)
  );
}

export async function saveAutomaticConversationMemory(
  env: Env,
  input: {
    conversationId: string;
    shortTerm: string;
    longTerm: string;
    consolidatedPosition: number;
    expectedRevision: number;
    updatedAt: number;
    force: boolean;
  }
): Promise<boolean> {
  const result = await run(
    env.DB.prepare(
      `
      UPDATE conversation_memories
      SET
        short_term = ?,
        long_term = ?,
        last_consolidated_position = ?,
        revision = revision + 1,
        updated_at = ?
      WHERE conversation_id = ?
        AND revision = ?
        AND (? = 1 OR last_consolidated_position < ?)
      `
    ).bind(
      input.shortTerm,
      input.longTerm,
      input.consolidatedPosition,
      input.updatedAt,
      input.conversationId,
      input.expectedRevision,
      input.force ? 1 : 0,
      input.consolidatedPosition
    )
  );
  return (result.meta.changes ?? 0) > 0;
}
