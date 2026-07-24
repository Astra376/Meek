import type { Env } from "../env";
import { publicAssetUrl } from "../lib/assets";
import { AppError } from "../lib/errors";

const IMAGE_DOWNLOAD_ATTEMPTS = 4;
const RETRYABLE_IMAGE_STATUSES = new Set([404, 408, 409, 425, 429, 500, 502, 503, 504]);

async function downloadGeneratedImage(imageUrl: string): Promise<Response> {
  let lastError: unknown;
  for (let attempt = 0; attempt < IMAGE_DOWNLOAD_ATTEMPTS; attempt += 1) {
    try {
      const response = await fetch(imageUrl);
      if (
        response.ok
        || !RETRYABLE_IMAGE_STATUSES.has(response.status)
        || attempt === IMAGE_DOWNLOAD_ATTEMPTS - 1
      ) {
        return response;
      }
      await response.body?.cancel();
    } catch (error) {
      lastError = error;
      if (attempt === IMAGE_DOWNLOAD_ATTEMPTS - 1) break;
    }
    await new Promise((resolve) => setTimeout(resolve, 400 * 2 ** attempt));
  }
  console.warn("Generated image download failed before receiving a usable response", {
    error: lastError instanceof Error ? lastError.name : "UnknownError"
  });
  throw new AppError(502, "IMAGE_FETCH_FAILED", "Could not download the generated image.");
}

export async function storeRemoteImageInR2(
  env: Env,
  key: string,
  imageUrl: string
): Promise<string> {
  if (!env.R2_PUBLIC_BASE_URL) {
    throw new AppError(500, "R2_BASE_URL_MISSING", "R2_PUBLIC_BASE_URL is not configured.");
  }
  const storedImageUrl = publicAssetUrl(env.R2_PUBLIC_BASE_URL, key);
  const response = await downloadGeneratedImage(imageUrl);
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
