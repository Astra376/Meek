import type { RequestContext } from "../env";

export interface RouteDefinition {
  method: "GET" | "POST" | "PATCH" | "DELETE";
  path: string;
  auth?: boolean;
  handler: (context: RequestContext) => Promise<Response>;
}

