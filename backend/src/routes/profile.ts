import {
  getMyProfile,
  getPublicProfile,
  getPublicProfileCharacters,
  updateMyProfile
} from "../services/profile";
import { json } from "../lib/response";
import { clampPageSize, parseCursor, parseJson, requireString } from "../lib/validation";
import type { RouteDefinition } from "./types";

export const profileRoutes: RouteDefinition[] = [
  {
    method: "GET",
    path: "/v1/profile/me",
    auth: true,
    handler: async (context) => json(await getMyProfile(context))
  },
  {
    method: "PATCH",
    path: "/v1/profile/me",
    auth: true,
    handler: async (context) => {
      const body = await parseJson<{ displayName?: string }>(context.request);
      return json(await updateMyProfile(context, requireString(body.displayName, "displayName", 80)));
    }
  },
  {
    method: "GET",
    path: "/v1/profiles/:userId",
    auth: true,
    handler: async (context) => json(await getPublicProfile(context, context.params.userId))
  },
  {
    method: "GET",
    path: "/v1/profiles/:userId/characters",
    auth: true,
    handler: async (context) => json(await getPublicProfileCharacters(
      context,
      context.params.userId,
      parseCursor(context.url.searchParams.get("cursor")),
      clampPageSize(context.url.searchParams.get("pageSize"))
    ))
  }
];
