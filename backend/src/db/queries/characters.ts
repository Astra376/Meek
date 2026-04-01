import type { Env } from "../../env";
import { all, first, run } from "../client";

export interface CharacterRecord {
  id: string;
  owner_user_id: string;
  name: string;
  tagline: string;
  description: string;
  system_prompt: string;
  visibility: "public" | "private";
  avatar_url: string | null;
  public_chat_count: number;
  like_count: number;
  liked_by_me: number;
  last_active_at: number;
  created_at: number;
  updated_at: number;
}

export async function insertCharacter(env: Env, input: {
  id: string;
  ownerUserId: string;
  name: string;
  tagline: string;
  description: string;
  systemPrompt: string;
  visibility: "public" | "private";
  avatarUrl: string | null;
  now: number;
}): Promise<void> {
  await run(
    env.DB.prepare(
      `
      INSERT INTO characters (
        id, owner_user_id, name, tagline, description, system_prompt, visibility,
        avatar_url, public_chat_count, like_count, last_active_at, created_at, updated_at
      )
      VALUES (?, ?, ?, ?, ?, ?, ?, ?, 0, 0, ?, ?, ?)
      `
    ).bind(
      input.id,
      input.ownerUserId,
      input.name,
      input.tagline,
      input.description,
      input.systemPrompt,
      input.visibility,
      input.avatarUrl,
      input.now,
      input.now,
      input.now
    )
  );
}

export async function updateCharacter(env: Env, input: {
  id: string;
  ownerUserId: string;
  name: string;
  tagline: string;
  description: string;
  systemPrompt: string;
  visibility: "public" | "private";
  avatarUrl: string | null;
  now: number;
}): Promise<void> {
  await run(
    env.DB.prepare(
      `
      UPDATE characters
      SET name = ?, tagline = ?, description = ?, system_prompt = ?, visibility = ?, avatar_url = ?, updated_at = ?
      WHERE id = ? AND owner_user_id = ?
      `
    ).bind(
      input.name,
      input.tagline,
      input.description,
      input.systemPrompt,
      input.visibility,
      input.avatarUrl,
      input.now,
      input.id,
      input.ownerUserId
    )
  );
}

export async function getCharacterById(env: Env, userId: string, characterId: string): Promise<CharacterRecord | null> {
  return first<CharacterRecord>(
    env.DB.prepare(
      `
      SELECT
        characters.*,
        CASE WHEN character_likes.user_id IS NULL THEN 0 ELSE 1 END AS liked_by_me
      FROM characters
      LEFT JOIN character_likes
        ON character_likes.character_id = characters.id
        AND character_likes.user_id = ?
      WHERE characters.id = ?
      LIMIT 1
      `
    ).bind(userId, characterId)
  );
}

export async function getOwnedCharacters(env: Env, userId: string, offset: number, limit: number): Promise<CharacterRecord[]> {
  return all<CharacterRecord>(
    env.DB.prepare(
      `
      SELECT characters.*, 0 AS liked_by_me
      FROM characters
      WHERE owner_user_id = ?
      ORDER BY updated_at DESC
      LIMIT ? OFFSET ?
      `
    ).bind(userId, limit, offset)
  );
}

export async function getLikedCharacters(env: Env, userId: string, offset: number, limit: number): Promise<CharacterRecord[]> {
  return all<CharacterRecord>(
    env.DB.prepare(
      `
      SELECT characters.*, 1 AS liked_by_me
      FROM character_likes
      INNER JOIN characters ON characters.id = character_likes.character_id
      WHERE character_likes.user_id = ? AND characters.visibility = 'public'
      ORDER BY characters.updated_at DESC
      LIMIT ? OFFSET ?
      `
    ).bind(userId, limit, offset)
  );
}

export async function getPublicFeed(env: Env, userId: string, offset: number, limit: number): Promise<CharacterRecord[]> {
  return all<CharacterRecord>(
    env.DB.prepare(
      `
      SELECT
        characters.*,
        CASE WHEN character_likes.user_id IS NULL THEN 0 ELSE 1 END AS liked_by_me
      FROM characters
      LEFT JOIN character_likes
        ON character_likes.character_id = characters.id
        AND character_likes.user_id = ?
      WHERE characters.visibility = 'public'
      ORDER BY characters.last_active_at DESC, characters.public_chat_count DESC, characters.created_at DESC
      LIMIT ? OFFSET ?
      `
    ).bind(userId, limit, offset)
  );
}

export async function searchPublicCharacters(
  env: Env,
  userId: string,
  query: string,
  offset: number,
  limit: number
): Promise<CharacterRecord[]> {
  const likeQuery = `%${query}%`;
  return all<CharacterRecord>(
    env.DB.prepare(
      `
      SELECT
        characters.*,
        CASE WHEN character_likes.user_id IS NULL THEN 0 ELSE 1 END AS liked_by_me
      FROM characters
      LEFT JOIN character_likes
        ON character_likes.character_id = characters.id
        AND character_likes.user_id = ?
      WHERE
        characters.visibility = 'public'
        AND (
          characters.name LIKE ?
          OR characters.tagline LIKE ?
          OR characters.description LIKE ?
        )
      ORDER BY characters.last_active_at DESC, characters.public_chat_count DESC, characters.created_at DESC
      LIMIT ? OFFSET ?
      `
    ).bind(userId, likeQuery, likeQuery, likeQuery, limit, offset)
  );
}

export async function likeCharacter(env: Env, userId: string, characterId: string, now: number): Promise<void> {
  await env.DB.batch([
    env.DB.prepare(
      `
      INSERT OR IGNORE INTO character_likes (user_id, character_id, created_at)
      VALUES (?, ?, ?)
      `
    ).bind(userId, characterId, now),
    env.DB.prepare(
      `
      UPDATE characters
      SET like_count = like_count + 1
      WHERE id = ? AND visibility = 'public'
        AND EXISTS (
          SELECT 1 FROM character_likes WHERE user_id = ? AND character_id = ?
        )
      `
    ).bind(characterId, userId, characterId)
  ]);
}

export async function unlikeCharacter(env: Env, userId: string, characterId: string): Promise<void> {
  await env.DB.batch([
    env.DB.prepare("DELETE FROM character_likes WHERE user_id = ? AND character_id = ?").bind(userId, characterId),
    env.DB.prepare(
      `
      UPDATE characters
      SET like_count = CASE WHEN like_count > 0 THEN like_count - 1 ELSE 0 END
      WHERE id = ?
      `
    ).bind(characterId)
  ]);
}

export async function incrementCharacterActivity(env: Env, characterId: string, now: number): Promise<void> {
  await run(
    env.DB.prepare(
      `
      UPDATE characters
      SET public_chat_count = public_chat_count + 1, last_active_at = ?, updated_at = ?
      WHERE id = ?
      `
    ).bind(now, now, characterId)
  );
}

