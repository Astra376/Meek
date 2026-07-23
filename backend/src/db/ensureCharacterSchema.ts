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

async function ensureCharacterSchemaImpl(env: Env): Promise<void> {
  const columns = await all<TableInfoRow>(env.DB.prepare("PRAGMA table_info(characters)"));
  if (columns.length === 0) {
    throw new AppError(
      500,
      "DB_MIGRATION_REQUIRED",
      "The backend database schema is missing the characters table."
    );
  }

  const existing = new Set(columns.map((column) => column.name));
  const statements: D1PreparedStatement[] = [];
  if (!existing.has("greeting")) {
    statements.push(
      env.DB.prepare("ALTER TABLE characters ADD COLUMN greeting TEXT NOT NULL DEFAULT ''")
    );
  }
  if (!existing.has("definition_private")) {
    statements.push(
      env.DB.prepare("ALTER TABLE characters ADD COLUMN definition_private INTEGER NOT NULL DEFAULT 0")
    );
  }

  for (const statement of statements) {
    try {
      await run(statement);
    } catch (error) {
      if (!isDuplicateColumnError(error)) throw error;
    }
  }
}

export function ensureCharacterSchema(env: Env): Promise<void> {
  const existing = readyByDatabase.get(env.DB);
  if (existing) return existing;

  const pending = ensureCharacterSchemaImpl(env).catch((error) => {
    readyByDatabase.delete(env.DB);
    throw error;
  });
  readyByDatabase.set(env.DB, pending);
  return pending;
}
