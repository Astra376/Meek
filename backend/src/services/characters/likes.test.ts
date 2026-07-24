import { describe, expect, it } from "vitest";
import type { Env, RequestContext } from "../../env";
import { characterRoutes } from "../../routes/characters";
import { likePublicCharacter, unlikePublicCharacter } from ".";

type BoundValue = string | number | null;

class FakeStatement {
  private bindings: BoundValue[] = [];

  constructor(
    private readonly database: FakeLikeDatabase,
    readonly sql: string
  ) {}

  bind(...values: BoundValue[]) {
    this.bindings = values;
    return this;
  }

  async all() {
    if (this.sql === "PRAGMA table_info(characters)") {
      return {
        results: [
          "id",
          "owner_user_id",
          "name",
          "tagline",
          "greeting",
          "description",
          "system_prompt",
          "definition_private",
          "visibility"
        ].map((name) => ({ name }))
      };
    }
    throw new Error(`Unexpected all query: ${this.sql}`);
  }

  async first<T>() {
    if (!this.sql.includes("WHERE characters.id = ?")) {
      throw new Error(`Unexpected first query: ${this.sql}`);
    }
    const [viewerUserId, characterId] = this.bindings as string[];
    if (characterId !== this.database.characterId) return null;
    return {
      id: this.database.characterId,
      owner_user_id: "creator-1",
      owner_display_name: "Creator",
      name: "Character",
      tagline: "Tagline",
      greeting: "Hello",
      description: "Description",
      system_prompt: "Private prompt",
      definition_private: 1,
      visibility: "public",
      avatar_url: null,
      public_chat_count: 3,
      like_count: this.database.likeCount,
      liked_by_me: this.database.likes.has(`${viewerUserId}:${characterId}`) ? 1 : 0,
      last_active_at: 1,
      created_at: 1,
      updated_at: 1
    } as T;
  }

  async run() {
    if (this.sql.includes("SET like_count = like_count + 1")) {
      const [characterId, userId] = this.bindings as string[];
      const key = `${userId}:${characterId}`;
      if (!this.database.likes.has(key)) this.database.likeCount += 1;
      return { meta: { changes: 1 } };
    }
    if (this.sql.includes("INSERT OR IGNORE INTO character_likes")) {
      const [userId, characterId] = this.bindings as string[];
      this.database.likes.add(`${userId}:${characterId}`);
      return { meta: { changes: 1 } };
    }
    if (this.sql.includes("SET like_count = CASE")) {
      const [characterId, userId] = this.bindings as string[];
      const key = `${userId}:${characterId}`;
      if (this.database.likes.has(key)) this.database.likeCount = Math.max(0, this.database.likeCount - 1);
      return { meta: { changes: 1 } };
    }
    if (this.sql.startsWith("DELETE FROM character_likes")) {
      const [userId, characterId] = this.bindings as string[];
      this.database.likes.delete(`${userId}:${characterId}`);
      return { meta: { changes: 1 } };
    }
    throw new Error(`Unexpected run query: ${this.sql}`);
  }
}

class FakeLikeDatabase {
  readonly characterId = "character-1";
  likeCount = 7;
  likes = new Set<string>();

  prepare(sql: string) {
    return new FakeStatement(this, sql.trim());
  }

  async batch<T extends D1PreparedStatement[]>(statements: T) {
    const results = [];
    for (const statement of statements as unknown as FakeStatement[]) {
      results.push(await statement.run());
    }
    return results;
  }
}

function context(database: FakeLikeDatabase): RequestContext {
  const request = new Request("https://example.test/v1/characters/character-1/like");
  return {
    request,
    env: { DB: database } as unknown as Env,
    url: new URL(request.url),
    params: { characterId: database.characterId },
    user: {
      userId: "viewer-1",
      tokenType: "access",
      exp: Number.MAX_SAFE_INTEGER
    }
  };
}

describe("public character likes", () => {
  it("returns authoritative state and keeps repeated like/unlike operations idempotent", async () => {
    const database = new FakeLikeDatabase();
    const requestContext = context(database);

    await expect(likePublicCharacter(requestContext, database.characterId)).resolves.toEqual({
      likedByMe: true,
      likeCount: 8
    });
    await expect(likePublicCharacter(requestContext, database.characterId)).resolves.toEqual({
      likedByMe: true,
      likeCount: 8
    });
    await expect(unlikePublicCharacter(requestContext, database.characterId)).resolves.toEqual({
      likedByMe: false,
      likeCount: 7
    });
    await expect(unlikePublicCharacter(requestContext, database.characterId)).resolves.toEqual({
      likedByMe: false,
      likeCount: 7
    });
  });

  it("returns the canonical engagement state from the HTTP mutation route", async () => {
    const database = new FakeLikeDatabase();
    const route = characterRoutes.find((candidate) =>
      candidate.method === "POST" && candidate.path === "/v1/characters/:characterId/like"
    );

    const response = await route!.handler(context(database));

    expect(response.status).toBe(200);
    await expect(response.json()).resolves.toEqual({
      likedByMe: true,
      likeCount: 8
    });
  });
});
