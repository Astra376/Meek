import type { RequestContext } from "../../env";
import { AppError } from "../../lib/errors";
import { getProfileByUserId, type ProfileRecord, updateProfile } from "../../db/queries/users";

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
