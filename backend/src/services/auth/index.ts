import type { Env, RequestContext } from "../../env";
import { createSessionTokens, parseRefreshToken } from "../../lib/auth";
import { AppError, assert } from "../../lib/errors";
import { createId } from "../../lib/ids";
import { getUserByGoogleSubject, upsertUserAndProfile } from "../../db/queries/users";

interface GoogleTokenInfoResponse {
  aud?: string;
  sub?: string;
  email?: string;
  name?: string;
  picture?: string;
}

async function verifyGoogleIdToken(env: Env, idToken: string): Promise<GoogleTokenInfoResponse> {
  const response = await fetch(`https://oauth2.googleapis.com/tokeninfo?id_token=${encodeURIComponent(idToken)}`);
  if (!response.ok) {
    throw new AppError(401, "GOOGLE_TOKEN_INVALID", "Google sign-in token could not be verified.");
  }

  const data = (await response.json()) as GoogleTokenInfoResponse;
  assert(data.aud === env.GOOGLE_WEB_CLIENT_ID, 401, "GOOGLE_TOKEN_INVALID", "Google token audience mismatch.");
  assert(data.sub, 401, "GOOGLE_TOKEN_INVALID", "Google token subject is missing.");
  assert(data.email, 401, "GOOGLE_TOKEN_INVALID", "Google token email is missing.");

  return data;
}

export async function exchangeGoogleToken(context: RequestContext, idToken: string): Promise<{
  accessToken: string;
  refreshToken: string;
  expiresAt: number;
}> {
  const tokenInfo = await verifyGoogleIdToken(context.env, idToken);
  const existing = await getUserByGoogleSubject(context.env, tokenInfo.sub!);
  const userId = existing?.id ?? createId("user");
  const now = Date.now();

  await upsertUserAndProfile(context.env, {
    userId,
    googleSubject: tokenInfo.sub!,
    email: tokenInfo.email!,
    displayName: tokenInfo.name?.trim() || tokenInfo.email!.split("@")[0],
    avatarUrl: tokenInfo.picture ?? null,
    now
  });

  const tokens = await createSessionTokens(context.env, userId);
  return {
    accessToken: tokens.accessToken,
    refreshToken: tokens.refreshToken,
    expiresAt: tokens.accessExpiresAt
  };
}

export async function refreshSession(context: RequestContext, refreshToken: string): Promise<{
  accessToken: string;
  refreshToken: string;
  expiresAt: number;
}> {
  const claims = await parseRefreshToken(context.env, refreshToken);
  const tokens = await createSessionTokens(context.env, claims.userId);
  return {
    accessToken: tokens.accessToken,
    refreshToken: tokens.refreshToken,
    expiresAt: tokens.accessExpiresAt
  };
}

