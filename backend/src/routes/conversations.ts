import { createConversation, getConversationDetail, listConversations } from "../services/conversations";
import { json } from "../lib/response";
import { clampPageSize, parseCursor, parseJson, requireString } from "../lib/validation";
import type { RouteDefinition } from "./types";

export const conversationRoutes: RouteDefinition[] = [
  {
    method: "GET",
    path: "/v1/conversations",
    auth: true,
    handler: async (context) => {
      const page = await listConversations(
        context,
        parseCursor(context.url.searchParams.get("cursor")),
        clampPageSize(context.url.searchParams.get("pageSize"), 20)
      );
      return json(page);
    }
  },
  {
    method: "POST",
    path: "/v1/conversations",
    auth: true,
    handler: async (context) => {
      const body = await parseJson<{ characterId?: string }>(context.request);
      const conversation = await createConversation(context, requireString(body.characterId, "characterId"));
      return json(conversation, { status: 201 });
    }
  },
  {
    method: "GET",
    path: "/v1/conversations/:conversationId",
    auth: true,
    handler: async (context) => json(await getConversationDetail(context, context.params.conversationId))
  }
];

