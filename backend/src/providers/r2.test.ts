import { afterEach, describe, expect, it, vi } from "vitest";
import type { Env } from "../env";
import { storeRemoteImageInR2 } from "./r2";

afterEach(() => {
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

describe("storeRemoteImageInR2", () => {
  it("stores validated image bytes and returns the Worker asset route URL", async () => {
    let storedBody: Uint8Array | undefined;
    const put = vi.fn(async (_key: string, value: ReadableStream, options: R2PutOptions) => {
      storedBody = new Uint8Array(await new Response(value).arrayBuffer());
      expect(options.httpMetadata).toEqual({ contentType: "image/jpeg" });
      return {} as R2Object;
    });
    vi.stubGlobal("fetch", vi.fn(async () => new Response(
      new Uint8Array([1, 2, 3]),
      { headers: { "Content-Type": "image/jpeg" } }
    )));
    const env = {
      ASSETS: { put },
      R2_PUBLIC_BASE_URL: "https://worker.example/v1/assets"
    } as unknown as Env;
    const key = "chat-backgrounds/user_1/background_1.jpg";

    await expect(storeRemoteImageInR2(env, key, "https://v3.fal.media/generated.jpg")).resolves.toBe(
      "https://worker.example/v1/assets/chat-backgrounds%2Fuser_1%2Fbackground_1.jpg"
    );
    expect(put).toHaveBeenCalledWith(
      key,
      expect.any(ReadableStream),
      { httpMetadata: { contentType: "image/jpeg" } }
    );
    expect(storedBody).toEqual(new Uint8Array([1, 2, 3]));
  });

  it("rejects a non-image upstream response without writing it to R2", async () => {
    const put = vi.fn();
    vi.stubGlobal("fetch", vi.fn(async () => new Response(
      "<html>error</html>",
      { headers: { "Content-Type": "text/html" } }
    )));
    const env = {
      ASSETS: { put },
      R2_PUBLIC_BASE_URL: "https://worker.example/v1/assets"
    } as unknown as Env;

    await expect(storeRemoteImageInR2(
      env,
      "portraits/user_1/portrait_1.jpg",
      "https://v3.fal.media/generated.jpg"
    )).rejects.toMatchObject({
      code: "IMAGE_FETCH_FAILED"
    });
    expect(put).not.toHaveBeenCalled();
  });

  it("retries a temporarily unavailable generated image before storing it", async () => {
    const put = vi.fn(async () => ({} as R2Object));
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(new Response("not ready", { status: 503 }))
      .mockResolvedValueOnce(new Response(
        new Uint8Array([4, 5, 6]),
        { headers: { "Content-Type": "image/jpeg" } }
      ));
    vi.stubGlobal("fetch", fetchMock);
    vi.spyOn(globalThis, "setTimeout").mockImplementation(((callback: () => void) => {
      callback();
      return 0;
    }) as typeof setTimeout);
    const env = {
      ASSETS: { put },
      R2_PUBLIC_BASE_URL: "https://worker.example/v1/assets"
    } as unknown as Env;

    await storeRemoteImageInR2(
      env,
      "chat-backgrounds/user_1/background_2.jpg",
      "https://v3.fal.media/generated.jpg"
    );

    expect(fetchMock).toHaveBeenCalledTimes(2);
    expect(put).toHaveBeenCalledOnce();
  });
});
