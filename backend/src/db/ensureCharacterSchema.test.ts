import { describe, expect, it } from "vitest";
import type { Env } from "../env";
import { ensureCharacterSchema } from "./ensureCharacterSchema";

class FakeStatement {
  constructor(
    private readonly database: FakeD1Database,
    private readonly sql: string
  ) {}

  async all() {
    if (this.sql !== "PRAGMA table_info(characters)") {
      throw new Error(`Unexpected all query: ${this.sql}`);
    }
    return { results: this.database.columns.map((name) => ({ name })) };
  }

  async run() {
    const match = this.sql.match(/^ALTER TABLE characters ADD COLUMN ([a-z_]+)/);
    if (!match) throw new Error(`Unexpected run query: ${this.sql}`);
    this.database.columns.push(match[1]);
    this.database.alterStatements.push(this.sql);
    return { meta: { changes: 1 } };
  }
}

class FakeD1Database {
  columns = [
    "id",
    "owner_user_id",
    "name",
    "tagline",
    "description",
    "system_prompt",
    "visibility"
  ];
  alterStatements: string[] = [];

  prepare(sql: string) {
    return new FakeStatement(this, sql.trim());
  }
}

describe("ensureCharacterSchema", () => {
  it("adds the columns required by character creation and conversation startup", async () => {
    const database = new FakeD1Database();
    const env = { DB: database } as unknown as Env;

    await ensureCharacterSchema(env);

    expect(database.columns).toContain("greeting");
    expect(database.columns).toContain("definition_private");
    expect(database.alterStatements).toEqual([
      "ALTER TABLE characters ADD COLUMN greeting TEXT NOT NULL DEFAULT ''",
      "ALTER TABLE characters ADD COLUMN definition_private INTEGER NOT NULL DEFAULT 0"
    ]);
  });
});
