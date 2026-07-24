import { describe, expect, it } from "vitest";
import type { Env } from "../../env";
import { findConversationByOwnerAndCharacter } from "./conversations";

class CaptureStatement {
  bindings: unknown[] = [];

  constructor(readonly sql: string) {}

  bind(...values: unknown[]) {
    this.bindings = values;
    return this;
  }

  async first() {
    return null;
  }
}

describe("findConversationByOwnerAndCharacter", () => {
  it("deterministically selects the most recently active conversation", async () => {
    let statement: CaptureStatement | undefined;
    const env = {
      DB: {
        prepare(sql: string) {
          statement = new CaptureStatement(sql);
          return statement;
        }
      }
    } as unknown as Env;

    await findConversationByOwnerAndCharacter(env, "user-1", "character-1");

    expect(statement?.sql).toContain("ORDER BY updated_at DESC, started_at DESC, id DESC");
    expect(statement?.sql).toContain("LIMIT 1");
    expect(statement?.bindings).toEqual(["user-1", "character-1"]);
  });
});
