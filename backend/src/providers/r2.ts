import type { Env } from "../env";
import { AppError } from "../lib/errors";

export async function storeRemoteImageInR2(
  env: Env,
  key: string,
  imageUrl: string
): Promise<string> {
  const response = await fetch(imageUrl);
  if (!response.ok || !response.body) {
    throw new AppError(502, "IMAGE_FETCH_FAILED", "Could not download the generated portrait.");
  }

  const contentType = response.headers.get("Content-Type") ?? "image/jpeg";
  await env.ASSETS.put(key, response.body, {
    httpMetadata: {
      contentType
    }
  });

  if (!env.R2_PUBLIC_BASE_URL) {
    throw new AppError(500, "R2_BASE_URL_MISSING", "R2_PUBLIC_BASE_URL is not configured.");
  }

  return `${env.R2_PUBLIC_BASE_URL.replace(/\/$/, "")}/${key}`;
}

