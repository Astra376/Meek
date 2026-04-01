import {
  createOwnedCharacter,
  getCharacter,
  getMyCharacters,
  getMyLikedCharacters,
  likePublicCharacter,
  unlikePublicCharacter,
  updateOwnedCharacter
} from "../services/characters";
import { json, noContent } from "../lib/response";
import { clampPageSize, parseCursor, parseJson, requireString } from "../lib/validation";
import type { RouteDefinition } from "./types";

export const characterRoutes: RouteDefinition[] = [
  {
    method: "POST",
    path: "/v1/characters",
    auth: true,
    handler: async (context) => {
      const body = await parseJson<Record<string, unknown>>(context.request);
      const created = await createOwnedCharacter(context, {
        name: requireString(body.name, "name", 80),
        tagline: requireString(body.tagline, "tagline", 140),
        description: requireString(body.description, "description", 1_200),
        systemPrompt: requireString(body.systemPrompt, "systemPrompt", 4_000),
        visibility: requireString(body.visibility, "visibility", 20) === "private" ? "private" : "public",
        avatarUrl: typeof body.avatarUrl === "string" ? body.avatarUrl : null
      });
      return json(created, { status: 201 });
    }
  },
  {
    method: "GET",
    path: "/v1/characters/me",
    auth: true,
    handler: async (context) => {
      const page = await getMyCharacters(
        context,
        parseCursor(context.url.searchParams.get("cursor")),
        clampPageSize(context.url.searchParams.get("pageSize"))
      );
      return json(page);
    }
  },
  {
    method: "GET",
    path: "/v1/characters/me/liked",
    auth: true,
    handler: async (context) => {
      const page = await getMyLikedCharacters(
        context,
        parseCursor(context.url.searchParams.get("cursor")),
        clampPageSize(context.url.searchParams.get("pageSize"))
      );
      return json(page);
    }
  },
  {
    method: "PATCH",
    path: "/v1/characters/:characterId",
    auth: true,
    handler: async (context) => {
      const body = await parseJson<Record<string, unknown>>(context.request);
      const updated = await updateOwnedCharacter(context, context.params.characterId, {
        name: requireString(body.name, "name", 80),
        tagline: requireString(body.tagline, "tagline", 140),
        description: requireString(body.description, "description", 1_200),
        systemPrompt: requireString(body.systemPrompt, "systemPrompt", 4_000),
        visibility: requireString(body.visibility, "visibility", 20) === "private" ? "private" : "public",
        avatarUrl: typeof body.avatarUrl === "string" ? body.avatarUrl : null
      });
      return json(updated);
    }
  },
  {
    method: "GET",
    path: "/v1/characters/:characterId",
    auth: true,
    handler: async (context) => json(await getCharacter(context, context.params.characterId))
  },
  {
    method: "POST",
    path: "/v1/characters/:characterId/like",
    auth: true,
    handler: async (context) => {
      await likePublicCharacter(context, context.params.characterId);
      return noContent();
    }
  },
  {
    method: "DELETE",
    path: "/v1/characters/:characterId/like",
    auth: true,
    handler: async (context) => {
      await unlikePublicCharacter(context, context.params.characterId);
      return noContent();
    }
  }
];
