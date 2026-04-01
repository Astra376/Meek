import type { RequestContext } from "../../env";
import { getPublicFeed, searchPublicCharacters } from "../../db/queries/characters";

function toCharacterDto(record: Awaited<ReturnType<typeof getPublicFeed>>[number]) {
  return {
    id: record.id,
    ownerUserId: record.owner_user_id,
    name: record.name,
    tagline: record.tagline,
    description: record.description,
    systemPrompt: record.system_prompt,
    visibility: record.visibility,
    avatarUrl: record.avatar_url,
    publicChatCount: record.public_chat_count,
    likeCount: record.like_count,
    likedByMe: Boolean(record.liked_by_me),
    lastActiveAt: record.last_active_at,
    createdAt: record.created_at,
    updatedAt: record.updated_at
  };
}

export async function getHomeFeed(context: RequestContext, cursor: number, limit: number) {
  const rows = await getPublicFeed(context.env, context.user!.userId, cursor, limit);
  return {
    items: rows.map(toCharacterDto),
    nextCursor: rows.length === limit ? String(cursor + rows.length) : null
  };
}

export async function searchHome(context: RequestContext, query: string, cursor: number, limit: number) {
  const rows = await searchPublicCharacters(context.env, context.user!.userId, query, cursor, limit);
  return {
    items: rows.map(toCharacterDto),
    nextCursor: rows.length === limit ? String(cursor + rows.length) : null
  };
}

