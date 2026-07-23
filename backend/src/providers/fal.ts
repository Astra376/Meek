import type { Env } from "../env";
import { AppError } from "../lib/errors";

interface FalQueueResponse {
  request_id: string;
}

interface FalStatusResponse {
  status: "IN_PROGRESS" | "COMPLETED" | "FAILED";
  response_url?: string;
}

interface FalResultResponse {
  images?: Array<{ url: string }>;
}

const FAL_STATUS_ATTEMPTS = 80;
const FAL_STATUS_POLL_INTERVAL_MS = 1_500;
const FAL_REQUEST_ATTEMPTS = 3;
const FAL_RETRYABLE_STATUSES = new Set([408, 429, 500, 502, 503, 504]);

async function fetchFal(url: string, init: RequestInit): Promise<Response> {
  let lastError: unknown;
  for (let attempt = 0; attempt < FAL_REQUEST_ATTEMPTS; attempt += 1) {
    try {
      const response = await fetch(url, init);
      if (!FAL_RETRYABLE_STATUSES.has(response.status) || attempt === FAL_REQUEST_ATTEMPTS - 1) {
        return response;
      }
      console.warn("Fal request will be retried", {
        status: response.status,
        method: init.method ?? "GET"
      });
      await response.body?.cancel();
    } catch (error) {
      lastError = error;
      if (attempt === FAL_REQUEST_ATTEMPTS - 1) throw error;
    }
    await new Promise((resolve) => setTimeout(resolve, 300 * 2 ** attempt));
  }
  throw lastError;
}

export async function generatePortraitWithFal(env: Env, prompt: string): Promise<string> {
  return generateImageWithFal(env, {
    model: env.FAL_MODEL,
    prompt,
    imageSize: "square_hd"
  });
}

export async function generateChatBackgroundWithFal(env: Env, prompt: string): Promise<string> {
  return generateImageWithFal(env, {
    model: env.FAL_BACKGROUND_MODEL || env.FAL_MODEL,
    prompt,
    imageSize: "landscape_16_9"
  });
}

async function generateImageWithFal(
  env: Env,
  options: {
    model: string;
    prompt: string;
    imageSize: string;
  }
): Promise<string> {
  const queueResponse = await fetchFal(`https://queue.fal.run/${options.model}`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      Authorization: `Key ${env.FAL_API_KEY}`
    },
    body: JSON.stringify({
      prompt: options.prompt,
      image_size: options.imageSize
    })
  });

  if (!queueResponse.ok) {
    throw new AppError(502, "FAL_QUEUE_ERROR", "Image generation could not be queued.");
  }

  const queued = (await queueResponse.json()) as FalQueueResponse;
  if (!queued.request_id) {
    throw new AppError(502, "FAL_QUEUE_ERROR", "Image generation did not return a request id.");
  }

  for (let attempt = 0; attempt < FAL_STATUS_ATTEMPTS; attempt += 1) {
    const statusResponse = await fetchFal(`https://queue.fal.run/${options.model}/requests/${queued.request_id}/status`, {
      headers: {
        Authorization: `Key ${env.FAL_API_KEY}`
      }
    });

    if (!statusResponse.ok) {
      throw new AppError(502, "FAL_STATUS_ERROR", "Image generation status failed.");
    }

    const status = (await statusResponse.json()) as FalStatusResponse;
    if (status.status === "FAILED") {
      throw new AppError(502, "FAL_FAILED", "Image generation failed.");
    }

    if (status.status === "COMPLETED") {
      const resultResponse = await fetchFal(
        `https://queue.fal.run/${options.model}/requests/${queued.request_id}`,
        {
          headers: {
            Authorization: `Key ${env.FAL_API_KEY}`
          }
        }
      );
      if (!resultResponse.ok) {
        throw new AppError(502, "FAL_RESULT_ERROR", "Image generation result fetch failed.");
      }
      const result = (await resultResponse.json()) as FalResultResponse;
      const imageUrl = result.images?.[0]?.url;
      if (!imageUrl) {
        throw new AppError(502, "FAL_RESULT_ERROR", "Image generation returned no image URL.");
      }
      return imageUrl;
    }

    await new Promise((resolve) => setTimeout(resolve, FAL_STATUS_POLL_INTERVAL_MS));
  }

  throw new AppError(504, "FAL_TIMEOUT", "Image generation timed out.");
}
