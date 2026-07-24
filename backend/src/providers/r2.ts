import type { Env } from "../env";
import { publicAssetUrl } from "../lib/assets";
import { AppError } from "../lib/errors";

export async function storeRemoteImageInR2(
  env: Env,
  key: string,
  imageUrl: string
): Promise<string> {
  if (!env.R2_PUBLIC_BASE_URL) {
    throw new AppError(500, "R2_BASE_URL_MISSING", "R2_PUBLIC_BASE_URL is not configured.");
  }
  const storedImageUrl = publicAssetUrl(env.R2_PUBLIC_BASE_URL, key);
  const response = await fetch(imageUrl);
  if (!response.ok || !response.body) {
    throw new AppError(502, "IMAGE_FETCH_FAILED", "Could not download the generated image.");
  }

  const contentType = response.headers.get("Content-Type") ?? "image/jpeg";
  if (!contentType.toLowerCase().startsWith("image/")) {
    await response.body.cancel();
    throw new AppError(502, "IMAGE_FETCH_FAILED", "The generated image response was invalid.");
  }
  await env.ASSETS.put(key, response.body, {
    httpMetadata: {
      contentType
    }
  });

  return storedImageUrl;
}
