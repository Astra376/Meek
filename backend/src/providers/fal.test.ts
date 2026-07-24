import { afterEach, describe, expect, it, vi } from "vitest";
import type { Env } from "../env";
import { generateChatBackgroundWithFal, generatePortraitWithFal } from "./fal";

function env(): Env {
  return {
    FAL_API_KEY: "test-key",
    FAL_MODEL: "fal-ai/nano-banana-2",
    R2_PUBLIC_BASE_URL: "https://example.invalid",
  } as Env;
}

function successfulFalFetch(requestBodies: string[]) {
  return vi.fn(async (_input: string | URL | Request, init?: RequestInit) => {
    if (init?.method === "POST") {
      requestBodies.push(String(init.body));
      return Response.json({ request_id: "request-1" });
    }
    const url = String(_input);
    if (url.endsWith("/status")) {
      return Response.json({ status: "COMPLETED" });
    }
    return Response.json({ images: [{ url: "https://images.example/generated.jpg" }] });
  });
}

afterEach(() => {
  vi.unstubAllGlobals();
});

describe("fal image request schema", () => {
  it("uses the configured model's aspect_ratio schema for chat backgrounds", async () => {
    const requestBodies: string[] = [];
    vi.stubGlobal("fetch", successfulFalFetch(requestBodies));

    await expect(generateChatBackgroundWithFal(env(), "a scene")).resolves.toBe(
      "https://images.example/generated.jpg"
    );

    expect(JSON.parse(requestBodies[0])).toMatchObject({
      prompt: "a scene",
      aspect_ratio: "16:9",
      output_format: "jpeg",
      num_images: 1,
    });
    expect(JSON.parse(requestBodies[0])).not.toHaveProperty("image_size");
  });

  it("requests square portraits through the same supported schema", async () => {
    const requestBodies: string[] = [];
    vi.stubGlobal("fetch", successfulFalFetch(requestBodies));

    await generatePortraitWithFal(env(), "a portrait");

    expect(JSON.parse(requestBodies[0]).aspect_ratio).toBe("1:1");
  });
});
