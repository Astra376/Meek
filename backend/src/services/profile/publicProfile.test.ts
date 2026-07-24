import { describe, expect, it } from "vitest";
import type { Env, RequestContext } from "../../env";
import { getPublicCharactersByOwner, type CharacterRecord } from "../../db/queries/characters";
import { getPublicProfileByUserId, type PublicProfileRecord } from "../../db/queries/users";
import { toCharacterDto, toPublicCharacterDto } from "../characters/characterDto";
import { getPublicProfile, toPublicProfileDto } from ".";

class CaptureStatement {
  bindings: unknown[] = [];

  constructor(readonly sql: string) {}

  bind(...values: unknown[]) {
    this.bindings = values;
    return this;
  }

  async all() {
    if (this.sql.trim() === "PRAGMA table_info(characters)") {
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
    return { results: [] };
  }

  async first() {
    if (this.sql.includes("COUNT(*) AS character_count")) {
      return {
        character_count: 2,
        created_at: 100,
        updated_at: 200
      };
    }
    return null;
  }
}

class CaptureDatabase {
  statements: CaptureStatement[] = [];

  prepare(sql: string) {
    const statement = new CaptureStatement(sql);
    this.statements.push(statement);
    return statement;
  }
}

describe("public creator profile privacy", () => {
  it("does not expose an email address from the public profile DTO", () => {
    const record = {
      user_id: "creator-1",
      display_name: "Creator",
      avatar_url: null,
      created_at: 1,
      updated_at: 2,
      character_count: 3,
      email: "private@example.test"
    } as PublicProfileRecord & { email: string };

    const dto = toPublicProfileDto(record);

    expect(dto).toEqual({
      userId: "creator-1",
      displayName: "Creator",
      avatarUrl: null,
      description: null,
      createdAt: 1,
      updatedAt: 2,
      characterCount: 3
    });
    expect(dto).not.toHaveProperty("email");
  });

  it("never exposes a character system prompt in the public creator list DTO", () => {
    const record: CharacterRecord = {
      id: "character-1",
      owner_user_id: "creator-1",
      owner_display_name: "Creator",
      name: "Character",
      tagline: "Tagline",
      greeting: "Hello",
      description: "Description",
      system_prompt: "highly private character definition",
      definition_private: 1,
      visibility: "public",
      avatar_url: null,
      public_chat_count: 10,
      like_count: 5,
      liked_by_me: 1,
      last_active_at: 3,
      created_at: 1,
      updated_at: 2
    };

    const dto = toPublicCharacterDto(record);

    expect(dto.systemPrompt).toBe("");
    expect(dto.likedByMe).toBe(true);
    expect(JSON.stringify(dto)).not.toContain("highly private character definition");
  });

  it("hides private definitions from non-owners across ordinary character DTOs", () => {
    const record: CharacterRecord = {
      id: "character-1",
      owner_user_id: "creator-1",
      owner_display_name: "Creator",
      name: "Character",
      tagline: "Tagline",
      greeting: "Hello",
      description: "Description",
      system_prompt: "highly private character definition",
      definition_private: 1,
      visibility: "public",
      avatar_url: null,
      public_chat_count: 10,
      like_count: 5,
      liked_by_me: 0,
      last_active_at: 3,
      created_at: 1,
      updated_at: 2
    };

    expect(toCharacterDto(record, "viewer-1").systemPrompt).toBe("");
    expect(toCharacterDto(record, "creator-1").systemPrompt).toBe("highly private character definition");
  });

  it("queries only public characters and computes likes for the authenticated viewer", async () => {
    const database = new CaptureDatabase();
    const env = { DB: database } as unknown as Env;

    await getPublicCharactersByOwner(env, "viewer-1", "creator-1", 20, 10);

    const listStatement = database.statements.find((statement) =>
      statement.sql.includes("FROM characters") && statement.sql.includes("character_likes")
    );
    expect(listStatement?.sql).toContain("characters.visibility = 'public'");
    expect(listStatement?.sql).not.toContain("characters.visibility = 'private'");
    expect(listStatement?.bindings).toEqual(["viewer-1", "creator-1", 10, 20]);
  });

  it("selects no email column for a public profile", async () => {
    const database = new CaptureDatabase();
    const env = { DB: database } as unknown as Env;

    await getPublicProfileByUserId(env, "creator-1");

    const query = database.statements[0];
    expect(query.sql.toLowerCase()).not.toContain("email");
    expect(query.sql).toContain("characters.visibility = 'public'");
    expect(query.bindings).toEqual(["creator-1"]);
  });

  it("returns a real virtual creator profile for system-owned characters", async () => {
    const database = new CaptureDatabase();
    const env = { DB: database } as unknown as Env;
    const request = new Request("https://example.test/v1/profiles/system");
    const context = {
      request,
      env,
      url: new URL(request.url),
      params: { userId: "system" },
      user: {
        userId: "viewer-1",
        tokenType: "access",
        exp: Number.MAX_SAFE_INTEGER
      }
    } satisfies RequestContext;

    await expect(getPublicProfile(context, "system")).resolves.toEqual({
      userId: "system",
      displayName: "Meek",
      avatarUrl: null,
      description: null,
      createdAt: 100,
      updatedAt: 200,
      characterCount: 2
    });
  });
});
