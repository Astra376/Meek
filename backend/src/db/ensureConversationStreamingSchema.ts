import type { Env } from "../env";
import { AppError } from "../lib/errors";
import { all, run } from "./client";

type TableInfoRow = {
  name: string;
};

const readyByDatabase = new WeakMap<D1Database, Promise<void>>();

function isDuplicateColumnError(error: unknown): boolean {
  const message = error instanceof Error ? error.message : String(error);
  return message.toLowerCase().includes("duplicate column name");
}

async function ensureConversationStreamingSchemaImpl(env: Env): Promise<void> {
  const columns = await all<TableInfoRow>(env.DB.prepare("PRAGMA table_info(conversations)"));
  if (columns.length === 0) {
    throw new AppError(
      500,
      "DB_MIGRATION_REQUIRED",
      "The backend database schema is missing the conversations table."
    );
  }

  const existing = new Set(columns.map((column) => column.name));
  const statements: D1PreparedStatement[] = [];

  if (!existing.has("version")) {
    statements.push(
      env.DB.prepare("ALTER TABLE conversations ADD COLUMN version INTEGER NOT NULL DEFAULT 0")
    );
  }
  if (!existing.has("active_run_id")) {
    statements.push(env.DB.prepare("ALTER TABLE conversations ADD COLUMN active_run_id TEXT"));
  }
  if (!existing.has("active_run_expires_at")) {
    statements.push(env.DB.prepare("ALTER TABLE conversations ADD COLUMN active_run_expires_at INTEGER"));
  }

  for (const statement of statements) {
    try {
      await run(statement);
    } catch (error) {
      if (isDuplicateColumnError(error)) {
        continue;
      }
      throw error;
    }
  }
}

export function ensureConversationStreamingSchema(env: Env): Promise<void> {
  const existing = readyByDatabase.get(env.DB);
  if (existing) {
    return existing;
  }

  const pending = ensureConversationStreamingSchemaImpl(env).catch((error) => {
    readyByDatabase.delete(env.DB);
    throw error;
  });
  readyByDatabase.set(env.DB, pending);
  return pending;
}
