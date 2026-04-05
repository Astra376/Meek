import type { RequestContext } from "../../env";
import { getPublicFeed, searchPublicCharacters } from "../../db/queries/characters";
import { toCharacterDto } from "../characters/characterDto";

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
