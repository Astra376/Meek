import { describe, expect, it, vi } from "vitest";
import type { Env, RequestContext } from "../../env";
import worker from "../../index";
import { assetRoutes } from "../../routes/assets";
import { getPublicAsset } from ".";

const KEY = "chat-backgrounds/user_1/background_1.jpg";

function storedObject(bytes = new Uint8Array([1, 2, 3])): R2ObjectBody {
  return {
    key: KEY,
    version: "version-1",
    size: bytes.byteLength,
    etag: "etag-1",
    httpEtag: "\"etag-1\"",
    checksums: {} as R2Checksums,
    uploaded: new Date("2026-01-02T03:04:05.000Z"),
    httpMetadata: { contentType: "image/jpeg" },
    writeHttpMetadata(headers: Headers) {
      headers.set("Content-Type", "image/jpeg");
    },
    customMetadata: {},
    range: undefined,
    storageClass: "Standard",
    ssecKeyMd5: undefined,
    body: new Response(bytes).body!,
    bodyUsed: false,
    arrayBuffer: async () => bytes.buffer,
    bytes: async () => bytes,
    text: async () => new TextDecoder().decode(bytes),
    json: async <T>() => JSON.parse(new TextDecoder().decode(bytes)) as T,
    blob: async () => new Blob([bytes])
  };
}

function context(
  get: ReturnType<typeof vi.fn>,
  request = new Request(`https://worker.example/v1/assets/${encodeURIComponent(KEY)}`)
): RequestContext {
  return {
    request,
    env: { ASSETS: { get } } as unknown as Env,
    url: new URL(request.url),
    params: { key: KEY }
  };
}

describe("public Worker asset route", () => {
  it("serves an allowed R2 image with immutable cache and entity headers", async () => {
    const get = vi.fn(async () => storedObject());

    const response = await getPublicAsset(context(get), KEY);

    expect(response.status).toBe(200);
    expect(response.headers.get("Content-Type")).toBe("image/jpeg");
    expect(response.headers.get("Cache-Control")).toBe("public, max-age=31536000, immutable");
    expect(response.headers.get("ETag")).toBe("\"etag-1\"");
    expect(response.headers.get("Last-Modified")).toBe("Fri, 02 Jan 2026 03:04:05 GMT");
    expect(response.headers.get("X-Content-Type-Options")).toBe("nosniff");
    expect(new Uint8Array(await response.arrayBuffer())).toEqual(new Uint8Array([1, 2, 3]));
    expect(get).toHaveBeenCalledWith(KEY);
    expect(assetRoutes[0].auth).toBeUndefined();
  });

  it("routes an encoded slash-containing key through the public Worker endpoint", async () => {
    const get = vi.fn(async () => storedObject());
    const request = new Request(`https://worker.example/v1/assets/${encodeURIComponent(KEY)}`);

    const response = await worker.fetch(
      request,
      { ASSETS: { get } } as unknown as Env,
      { waitUntil: vi.fn() } as unknown as ExecutionContext
    );

    expect(response.status).toBe(200);
    expect(response.headers.get("Access-Control-Allow-Origin")).toBe("*");
    expect(get).toHaveBeenCalledWith(KEY);
  });

  it("routes legacy asset URLs whose key contains literal slashes", async () => {
    const get = vi.fn(async () => storedObject());
    const request = new Request(`https://worker.example/v1/assets/${KEY}`);

    const response = await worker.fetch(
      request,
      { ASSETS: { get } } as unknown as Env,
      { waitUntil: vi.fn() } as unknown as ExecutionContext
    );

    expect(response.status).toBe(200);
    expect(get).toHaveBeenCalledWith(KEY);
  });

  it("honors If-None-Match without returning the object body", async () => {
    const get = vi.fn(async () => storedObject());
    const request = new Request(`https://worker.example/v1/assets/${encodeURIComponent(KEY)}`, {
      headers: { "If-None-Match": "\"etag-1\"" }
    });

    const response = await getPublicAsset(context(get, request), KEY);

    expect(response.status).toBe(304);
    expect(response.body).toBeNull();
    expect(response.headers.get("ETag")).toBe("\"etag-1\"");
  });

  it("rejects arbitrary R2 keys before accessing the bucket", async () => {
    const get = vi.fn();

    await expect(getPublicAsset(context(get), "../private/object")).rejects.toMatchObject({
      status: 404,
      code: "ASSET_NOT_FOUND"
    });
    expect(get).not.toHaveBeenCalled();
  });

  it("returns not found when an allowed object does not exist", async () => {
    const get = vi.fn(async () => null);

    await expect(getPublicAsset(context(get), KEY)).rejects.toMatchObject({
      status: 404,
      code: "ASSET_NOT_FOUND"
    });
  });
});
