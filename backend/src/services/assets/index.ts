import type { RequestContext } from "../../env";
import { PUBLIC_ASSET_CACHE_CONTROL, requirePublicAssetKey } from "../../lib/assets";
import { AppError } from "../../lib/errors";

function fallbackContentType(key: string): string {
  if (key.endsWith(".png")) return "image/png";
  if (key.endsWith(".webp")) return "image/webp";
  return "image/jpeg";
}

function matchesEtag(ifNoneMatch: string | null, etag: string): boolean {
  if (!ifNoneMatch) return false;
  return ifNoneMatch.split(",").some((candidate) => {
    const normalized = candidate.trim();
    return normalized === "*" || normalized === etag || normalized === `W/${etag}`;
  });
}

export async function getPublicAsset(context: RequestContext, rawKey: string): Promise<Response> {
  const key = requirePublicAssetKey(rawKey);
  const object = await context.env.ASSETS.get(key);
  if (!object) {
    throw new AppError(404, "ASSET_NOT_FOUND", "Asset not found.");
  }

  const headers = new Headers({
    "Cache-Control": PUBLIC_ASSET_CACHE_CONTROL,
    "Content-Length": String(object.size),
    "Content-Type": object.httpMetadata?.contentType ?? fallbackContentType(key),
    "ETag": object.httpEtag,
    "Last-Modified": object.uploaded.toUTCString(),
    "X-Content-Type-Options": "nosniff"
  });

  if (matchesEtag(context.request.headers.get("If-None-Match"), object.httpEtag)) {
    await object.body.cancel();
    return new Response(null, { status: 304, headers });
  }

  return new Response(object.body, { status: 200, headers });
}
