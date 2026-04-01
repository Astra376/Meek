import { generateCharacterPortrait } from "../services/images";
import { json } from "../lib/response";
import { parseJson, requireString } from "../lib/validation";
import type { RouteDefinition } from "./types";

export const imageRoutes: RouteDefinition[] = [
  {
    method: "POST",
    path: "/v1/images/generate-character-portrait",
    auth: true,
    handler: async (context) => {
      const body = await parseJson<{ prompt?: string }>(context.request);
      return json(await generateCharacterPortrait(context, requireString(body.prompt, "prompt", 1_000)));
    }
  }
];

