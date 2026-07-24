import { getPublicAsset } from "../services/assets";
import type { RouteDefinition } from "./types";

export const assetRoutes: RouteDefinition[] = [
  {
    method: "GET",
    path: "/v1/assets/:key",
    handler: async (context) => getPublicAsset(context, context.params.key)
  }
];
