import { exchangeGoogleToken, refreshSession } from "../services/auth";
import { json, noContent } from "../lib/response";
import { parseJson, requireString } from "../lib/validation";
import type { RouteDefinition } from "./types";

export const authRoutes: RouteDefinition[] = [
  {
    method: "POST",
    path: "/v1/auth/google",
    handler: async (context) => {
      const body = await parseJson<{ idToken?: string }>(context.request);
      const session = await exchangeGoogleToken(context, requireString(body.idToken, "idToken"));
      return json(session, { status: 201 });
    }
  },
  {
    method: "POST",
    path: "/v1/auth/refresh",
    handler: async (context) => {
      const body = await parseJson<{ refreshToken?: string }>(context.request);
      const session = await refreshSession(context, requireString(body.refreshToken, "refreshToken"));
      return json(session);
    }
  },
  {
    method: "POST",
    path: "/v1/auth/logout",
    handler: async () => noContent()
  }
];

