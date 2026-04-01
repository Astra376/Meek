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

export async function generatePortraitWithFal(env: Env, prompt: string): Promise<string> {
  const queueResponse = await fetch(`https://queue.fal.run/${env.FAL_MODEL}`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      Authorization: `Key ${env.FAL_API_KEY}`
    },
    body: JSON.stringify({
      prompt,
      image_size: "square_hd"
    })
  });

  if (!queueResponse.ok) {
    throw new AppError(502, "FAL_QUEUE_ERROR", "Image generation could not be queued.");
  }

  const queued = (await queueResponse.json()) as FalQueueResponse;
  if (!queued.request_id) {
    throw new AppError(502, "FAL_QUEUE_ERROR", "Image generation did not return a request id.");
  }

  for (let attempt = 0; attempt < 30; attempt += 1) {
    const statusResponse = await fetch(`https://queue.fal.run/${env.FAL_MODEL}/requests/${queued.request_id}/status`, {
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
      const resultResponse = await fetch(
        `https://queue.fal.run/${env.FAL_MODEL}/requests/${queued.request_id}`
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

    await new Promise((resolve) => setTimeout(resolve, 1_500));
  }

  throw new AppError(504, "FAL_TIMEOUT", "Image generation timed out.");
}
