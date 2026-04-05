import type { CharacterRecord } from "../../db/queries/characters";

function normalizeAuthorUsername(value: string): string {
  return value
    .trim()
    .toLowerCase()
    .replace(/^@+/, "")
    .replace(/[^a-z0-9._]+/g, "_")
    .replace(/^[_\.]+|[_\.]+$/g, "");
}

function authorUsername(record: CharacterRecord): string {
  if (record.owner_user_id === "system") {
    return "characterchat";
  }

  return normalizeAuthorUsername(record.owner_display_name ?? "")
    || normalizeAuthorUsername(record.owner_user_id)
    || "creator";
}

export function toCharacterDto(record: CharacterRecord) {
  return {
    id: record.id,
    ownerUserId: record.owner_user_id,
    authorUsername: authorUsername(record),
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
