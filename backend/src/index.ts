import type { Env, RequestContext } from "./env";
import { processOfflineMessages } from "./services/chat/offline";
import { requireAuth } from "./lib/auth";
import { handleError, json } from "./lib/response";
import { authRoutes } from "./routes/auth";
import { characterRoutes } from "./routes/characters";
import { chatRoutes } from "./routes/chat";
import { conversationRoutes } from "./routes/conversations";
import { homeRoutes } from "./routes/home";
import { imageRoutes } from "./routes/images";
import { profileRoutes } from "./routes/profile";
import type { RouteDefinition } from "./routes/types";

const routes: RouteDefinition[] = [
  ...authRoutes,
  ...profileRoutes,
  ...characterRoutes,
  ...homeRoutes,
  ...conversationRoutes,
  ...chatRoutes,
  ...imageRoutes
];

function matchPath(template: string, actualPath: string): Record<string, string> | null {
  const templateSegments = template.split("/").filter(Boolean);
  const actualSegments = actualPath.split("/").filter(Boolean);
  if (templateSegments.length !== actualSegments.length) return null;

  const params: Record<string, string> = {};
  for (let index = 0; index < templateSegments.length; index += 1) {
    const templateSegment = templateSegments[index];
    const actualSegment = actualSegments[index];
    if (templateSegment.startsWith(":")) {
      params[templateSegment.slice(1)] = decodeURIComponent(actualSegment);
      continue;
    }
    if (templateSegment !== actualSegment) return null;
  }

  return params;
}

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    try {
      if (request.method === "OPTIONS") {
        return new Response(null, {
          status: 204,
          headers: {
            "Access-Control-Allow-Origin": "*",
            "Access-Control-Allow-Methods": "GET,POST,PATCH,DELETE,OPTIONS",
            "Access-Control-Allow-Headers": "Authorization,Content-Type"
          }
        });
      }

      const url = new URL(request.url);
      const route = routes.find((candidate) => candidate.method === request.method && matchPath(candidate.path, url.pathname));
      if (!route) {
        return json({ code: "NOT_FOUND", message: "Route not found." }, { status: 404 });
      }

      const params = matchPath(route.path, url.pathname) ?? {};
      const context: RequestContext = {
        request,
        env,
        url,
        params
      };

      if (route.auth) {
        context.user = await requireAuth(context);
      }

      const response = await route.handler(context);
      const headers = new Headers(response.headers);
      headers.set("Access-Control-Allow-Origin", "*");
      return new Response(response.body, {
        status: response.status,
        statusText: response.statusText,
        headers
      });
    } catch (error) {
      return handleError(error);
    }
  },
  async scheduled(event: object, env: Env, ctx: { waitUntil(promise: Promise<any>): void }) {
    ctx.waitUntil(processOfflineMessages(env));
  }
};
