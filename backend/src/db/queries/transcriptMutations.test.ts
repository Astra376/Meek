import { DatabaseSync } from "node:sqlite";
import { describe, expect, it } from "vitest";
import type { Env } from "../../env";
import { insertMessage, listContextMessages } from "./conversations";
import { editMessageAtomically, rewindToMessageAtomically, selectRegenerationAtomically } from "./transcriptMutations";

function database() {
  const sqlite = new DatabaseSync(":memory:");
  sqlite.exec(`
    PRAGMA foreign_keys = ON;
    CREATE TABLE conversations (id TEXT PRIMARY KEY, owner_user_id TEXT, active_run_id TEXT, active_run_expires_at INTEGER,
      updated_at INTEGER DEFAULT 0, last_message_at INTEGER, version INTEGER DEFAULT 0);
    CREATE TABLE messages (id TEXT PRIMARY KEY, conversation_id TEXT, position INTEGER, role TEXT,
      content TEXT, edited INTEGER, created_at INTEGER, updated_at INTEGER, selected_regeneration_id TEXT);
    CREATE TABLE assistant_regenerations (id TEXT PRIMARY KEY, message_id TEXT REFERENCES messages(id) ON DELETE CASCADE,
      content TEXT, created_at INTEGER);
    INSERT INTO conversations (id, owner_user_id) VALUES ('conversation', 'owner');
    INSERT INTO messages VALUES ('user-1', 'conversation', 0, 'user', 'Hello', 0, 0, 0, NULL);
    INSERT INTO messages VALUES ('assistant-1', 'conversation', 1, 'assistant', 'Original', 0, 0, 0, 'version-1');
    INSERT INTO assistant_regenerations VALUES ('version-1', 'assistant-1', 'Alternate', 0);
    INSERT INTO messages VALUES ('user-2', 'conversation', 2, 'user', 'Again', 0, 0, 0, NULL);
    INSERT INTO messages VALUES ('assistant-2', 'conversation', 3, 'assistant', 'Second', 0, 0, 0, NULL);
    INSERT INTO assistant_regenerations VALUES ('version-2', 'assistant-2', 'Second alternate', 0);
  `);
  const env = { DB: {
    prepare(sql: string) {
      return { bind(...args: any[]) { return {
        async run() {
          const statement = sqlite.prepare(sql);
          if (statement.columns().length) return { success: true, results: statement.all(...args), meta: { changes: 0 } };
          const result = statement.run(...args);
          return { success: true, meta: { changes: Number(result.changes) } };
        },
        async all() { return { results: sqlite.prepare(sql).all(...args) }; }
      }; } };
    },
    async batch(statements: Array<{ run(): Promise<unknown> }>) {
      sqlite.exec("BEGIN");
      try {
        const result = [];
        for (const statement of statements) result.push(await statement.run());
        sqlite.exec("COMMIT");
        return result;
      } catch (error) { sqlite.exec("ROLLBACK"); throw error; }
    }
  } } as unknown as Env;
  return { env, sqlite };
}

describe("atomic transcript changes", () => {
  it("rewinds to the explicit message ID; repeated taps do not accumulate backwards", async () => {
    const { env, sqlite } = database();
    try {
      await rewindToMessageAtomically(env, "owner", "conversation", "assistant-1", 1);
      await rewindToMessageAtomically(env, "owner", "conversation", "assistant-1", 2);
      expect(sqlite.prepare("SELECT id FROM messages ORDER BY position").all().map((row) => row.id))
        .toEqual(["user-1", "assistant-1"]);
      expect(sqlite.prepare("SELECT id FROM assistant_regenerations").all().map((row) => row.id)).toEqual(["version-1"]);
      expect(sqlite.prepare("SELECT version FROM conversations").get()?.version).toBe(1);
      // A stale request for a removed ID cannot reinterpret its position.
      await expect(rewindToMessageAtomically(env, "owner", "conversation", "assistant-2", 3))
        .rejects.toMatchObject({ status: 409, code: "TRANSCRIPT_CHANGED" });
      expect(sqlite.prepare("SELECT COUNT(*) AS count FROM messages").get()?.count).toBe(2);
    } finally { sqlite.close(); }
  });

  it("edits the selected version without changing the original or other versions", async () => {
    const { env, sqlite } = database();
    try {
      expect(await editMessageAtomically(env, "owner", "conversation", "assistant-1", "Edited", 1)).toBe(true);
      expect(sqlite.prepare("SELECT content, edited FROM messages WHERE id='assistant-1'").get())
        .toMatchObject({ content: "Original", edited: 1 });
      expect(sqlite.prepare("SELECT content FROM assistant_regenerations WHERE id='version-1'").get()?.content).toBe("Edited");
      expect(sqlite.prepare("SELECT content FROM assistant_regenerations WHERE id='version-2'").get()?.content).toBe("Second alternate");
    } finally { sqlite.close(); }
  });

  it("forbids switching an older reply to either an alternate or its original version", async () => {
    const { env, sqlite } = database();
    try {
      expect(await selectRegenerationAtomically(env, "owner", "conversation", "assistant-1", null, 1)).toBe(false);
      expect(await selectRegenerationAtomically(env, "owner", "conversation", "assistant-1", "version-1", 1)).toBe(false);
      expect(await selectRegenerationAtomically(env, "owner", "conversation", "assistant-2", "version-1", 1)).toBe(false);
      expect(await selectRegenerationAtomically(env, "owner", "conversation", "assistant-2", "version-2", 1)).toBe(true);
      expect(await selectRegenerationAtomically(env, "owner", "conversation", "assistant-2", null, 1)).toBe(true);
    } finally { sqlite.close(); }
  });

  it("does not let invalid requests or other owners cancel a running reply", async () => {
    const { env, sqlite } = database();
    try {
      sqlite.exec("UPDATE conversations SET active_run_id='run', active_run_expires_at=500");
      expect(await selectRegenerationAtomically(env, "owner", "conversation", "assistant-2", "version-2", 1)).toBe(false);
      expect(await editMessageAtomically(env, "owner", "conversation", "missing", "Edited", 1)).toBe(false);
      await expect(rewindToMessageAtomically(env, "owner", "conversation", "missing", 1))
        .rejects.toMatchObject({ status: 409, code: "TRANSCRIPT_CHANGED" });
      expect(sqlite.prepare("SELECT COUNT(*) AS count FROM messages").get()?.count).toBe(4);
      expect(await editMessageAtomically(env, "intruder", "conversation", "assistant-1", "Edited", 1)).toBe(false);
      await expect(rewindToMessageAtomically(env, "intruder", "conversation", "assistant-1", 1))
        .rejects.toMatchObject({ status: 409, code: "TRANSCRIPT_CHANGED" });
      expect(sqlite.prepare("SELECT COUNT(*) AS count FROM messages").get()?.count).toBe(4);
      expect(sqlite.prepare("SELECT version FROM conversations").get()?.version).toBe(0);
      expect(sqlite.prepare("SELECT active_run_id FROM conversations").get()?.active_run_id).toBe("run");
    } finally { sqlite.close(); }
  });

  it.each(["edit", "rewind"])("%s cancels a pending generation and rejects its late saved reply", async (operation) => {
    const { env, sqlite } = database();
    try {
      const now = Date.now();
      sqlite.prepare("UPDATE conversations SET active_run_id='run', active_run_expires_at=?").run(now + 75_000);
      if (operation === "edit") {
        expect(await editMessageAtomically(env, "owner", "conversation", "user-1", "Corrected", now)).toBe(true);
        expect(sqlite.prepare("SELECT content FROM messages WHERE id='user-1'").get()?.content).toBe("Corrected");
      } else {
        expect(await rewindToMessageAtomically(env, "owner", "conversation", "assistant-1", now)).toBe(2);
        expect(sqlite.prepare("SELECT COUNT(*) AS count FROM messages").get()?.count).toBe(2);
      }
      expect(sqlite.prepare("SELECT active_run_id, active_run_expires_at, version FROM conversations").get())
        .toMatchObject({ active_run_id: null, active_run_expires_at: null, version: 1 });
      await expect(insertMessage(env, {
        id: "late-reply", conversation_id: "conversation", position: 4, role: "assistant",
        content: "Old context", edited: 0, created_at: now, updated_at: now, selected_regeneration_id: null
      }, "run")).rejects.toMatchObject({ code: "RUN_CANCELLED" });
      expect(sqlite.prepare("SELECT id FROM messages WHERE id='late-reply'").get()).toBeUndefined();
    } finally { sqlite.close(); }
  });

  it("reads only the selected versions for model context with a bounded recent history", async () => {
    const { env, sqlite } = database();
    try {
      const initial = await listContextMessages(env, "conversation");
      expect(initial[1].content).toBe("Alternate");
      expect(initial[3].content).toBe("Second");
      const insert = sqlite.prepare("INSERT INTO messages VALUES (?, 'conversation', ?, 'user', 'text', 0, 0, 0, NULL)");
      for (let index = 4; index < 300; index++) insert.run(`message-${index}`, index);
      const recent = await listContextMessages(env, "conversation");
      expect(recent).toHaveLength(256);
      expect(recent[0].position).toBe(44);
      expect(recent.at(-1)?.position).toBe(299);
    } finally { sqlite.close(); }
  });
});
