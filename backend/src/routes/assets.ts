import { getPublicAsset } from "../services/assets";
import type { RouteDefinition } from "./types";

export const assetRoutes: RouteDefinition[] = [
  {
    method: "GET",
    // Keep accepting the original unescaped R2 URLs as well as canonical
    // URLs whose complete key is encoded into a single path segment.
    path: "/v1/assets/*key",
    handler: async (context) => getPublicAsset(context, context.params.key)
  }
];
