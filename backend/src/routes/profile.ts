import { getMyProfile, updateMyProfile } from "../services/profile";
import { json } from "../lib/response";
import { parseJson, requireString } from "../lib/validation";
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
  }
];

