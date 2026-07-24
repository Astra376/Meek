import { beforeEach, describe, expect, it, vi } from "vitest";
import type { Env, RequestContext } from "../../env";
import { generateChatBackgroundWithFal } from "../../providers/fal";
import { storeRemoteImageInR2 } from "../../providers/r2";
import { generateChatBackground } from ".";

vi.mock("../../providers/fal", () => ({
  generateChatBackgroundWithFal: vi.fn(),
  generatePortraitWithFal: vi.fn()
}));

vi.mock("../../providers/r2", () => ({
  storeRemoteImageInR2: vi.fn()
}));

describe("generateChatBackground", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("reuses an image stored by an earlier request with the same scene key", async () => {
    const head = vi.fn(async () => ({ key: "existing" }));
    const context = {
      env: {
        ASSETS: { head },
        R2_PUBLIC_BASE_URL: "https://worker.example/v1/assets"
      } as unknown as Env,
      user: { userId: "user_1" }
    } as RequestContext;

    const result = await generateChatBackground(context, "scene prompt", "initial:abc123");

    expect(result.imageUrl).toBe(
      "https://worker.example/v1/assets/chat-backgrounds%2Fuser_1%2Finitial_abc123.jpg"
    );
    expect(generateChatBackgroundWithFal).not.toHaveBeenCalled();
    expect(storeRemoteImageInR2).not.toHaveBeenCalled();
  });

  it("stores a newly generated image under the stable scene key", async () => {
    const head = vi.fn(async () => null);
    vi.mocked(generateChatBackgroundWithFal).mockResolvedValue("https://fal.example/result.jpg");
    vi.mocked(storeRemoteImageInR2).mockResolvedValue("https://worker.example/background.jpg");
    const context = {
      env: {
        ASSETS: { head },
        R2_PUBLIC_BASE_URL: "https://worker.example/v1/assets"
      } as unknown as Env,
      user: { userId: "user_1" }
    } as RequestContext;

    const result = await generateChatBackground(context, "scene prompt", "scene:def456");

    expect(result.imageUrl).toBe("https://worker.example/background.jpg");
    expect(storeRemoteImageInR2).toHaveBeenCalledWith(
      context.env,
      "chat-backgrounds/user_1/scene_def456.jpg",
      "https://fal.example/result.jpg"
    );
  });
});
