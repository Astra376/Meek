import type { RequestContext } from "../../env";
import { AppError } from "../../lib/errors";
import {
  getPublicCharacterOwnerStats,
  getPublicCharactersByOwner
} from "../../db/queries/characters";
import {
  getProfileByUserId,
  getPublicProfileByUserId,
  type ProfileRecord,
  type PublicProfileRecord,
  updateProfile
} from "../../db/queries/users";
import { toPublicCharacterDto } from "../characters/characterDto";

function toProfileDto(profile: ProfileRecord | null) {
  if (!profile) return null;
  return {
    userId: profile.user_id,
    email: profile.email ?? "",
    displayName: profile.display_name,
    avatarUrl: profile.avatar_url,
    createdAt: profile.created_at,
    updatedAt: profile.updated_at
  };
}

export function toPublicProfileDto(profile: PublicProfileRecord) {
  return {
    userId: profile.user_id,
    displayName: profile.display_name,
    avatarUrl: profile.avatar_url,
    description: null,
    createdAt: profile.created_at,
    updatedAt: profile.updated_at,
    characterCount: profile.character_count,
    interactionCount: profile.interaction_count,
    likeCount: profile.like_count
  };
}

async function findPublicProfile(
  context: RequestContext,
  userId: string
): Promise<PublicProfileRecord | null> {
  const profile = await getPublicProfileByUserId(context.env, userId);
  if (profile || userId !== "system") return profile;

  const stats = await getPublicCharacterOwnerStats(context.env, userId);
  return {
    user_id: "system",
    display_name: "Meek",
    avatar_url: null,
    created_at: stats.created_at,
    updated_at: stats.updated_at,
    character_count: stats.character_count,
    interaction_count: stats.interaction_count,
    like_count: stats.like_count
  };
}

export async function getMyProfile(context: RequestContext) {
  const profile = await getProfileByUserId(context.env, context.user!.userId);
  if (!profile) {
    throw new AppError(404, "PROFILE_NOT_FOUND", "Profile not found.");
  }
  return toProfileDto(profile);
}

export async function updateMyProfile(context: RequestContext, displayName: string) {
  await updateProfile(context.env, context.user!.userId, displayName, Date.now());
  return getMyProfile(context);
}

export async function getPublicProfile(context: RequestContext, userId: string) {
  const profile = await findPublicProfile(context, userId);
  if (!profile) {
    throw new AppError(404, "PROFILE_NOT_FOUND", "Profile not found.");
  }
  return toPublicProfileDto(profile);
}

export async function getPublicProfileCharacters(
  context: RequestContext,
  userId: string,
  cursor: number,
  limit: number
) {
  const profile = await findPublicProfile(context, userId);
  if (!profile) {
    throw new AppError(404, "PROFILE_NOT_FOUND", "Profile not found.");
  }
  const records = await getPublicCharactersByOwner(
    context.env,
    context.user!.userId,
    userId,
    cursor,
    limit
  );
  return {
    items: records.map(toPublicCharacterDto),
    nextCursor: records.length === limit ? String(cursor + records.length) : null
  };
}
