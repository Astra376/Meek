import { afterEach, describe, expect, it, vi } from "vitest";
import { completeChatText, streamChatText } from "./openrouter";
import type { Env } from "../env";

const env = {
  OPENROUTER_API_KEY: "test-key",
  OPENROUTER_MODEL: "test-model"
} as Env;

function streamFromText(text: string): ReadableStream<Uint8Array> {
  const encoded = new TextEncoder().encode(text);
  return new ReadableStream<Uint8Array>({
    start(controller) {
      controller.enqueue(encoded);
      controller.close();
    }
  });
}

describe("streamChatText", () => {
  afterEach(() => {
    vi.unstubAllGlobals();
    vi.restoreAllMocks();
  });

  it("parses OpenRouter SSE chunks with CRLF line endings", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(async () => new Response(
        streamFromText(
          [
            'data: {"choices":[{"delta":{"content":"hello"}}]}',
            "",
            'data: {"choices":[{"delta":{"content":" there"}}]}',
            "",
            "data: [DONE]",
            ""
          ].join("\r\n")
        ),
        { status: 200 }
      ))
    );

    const chunks: string[] = [];
    for await (const chunk of streamChatText(env, [{ role: "user", content: "hi" }])) {
      chunks.push(chunk);
    }

    expect(chunks).toEqual(["hello", " there"]);
  });

  it("requests configured model fallbacks in priority order", async () => {
    const fetchMock = vi.fn(async (_url: string, init?: RequestInit) => new Response(
      JSON.stringify({ choices: [{ message: { content: "ready" } }] }),
      { status: 200 }
    ));
    vi.stubGlobal("fetch", fetchMock);

    const value = await completeChatText(
      { ...env, OPENROUTER_FALLBACK_MODELS: "fallback-one, fallback-two" },
      [{ role: "user", content: "hi" }]
    );

    expect(value).toBe("ready");
    const request = JSON.parse(String(fetchMock.mock.calls[0]?.[1]?.body));
    expect(request.models).toEqual(["test-model", "fallback-one", "fallback-two"]);
    expect(request.provider).toEqual({ allow_fallbacks: true });
  });

  it("retries a transient provider response before streaming", async () => {
    vi.stubGlobal("setTimeout", (callback: () => void) => {
      callback();
      return 0;
    });
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(new Response(
        JSON.stringify({ error: { code: 503, message: "provider unavailable" } }),
        { status: 503 }
      ))
      .mockResolvedValueOnce(new Response(
        streamFromText('data: {"choices":[{"delta":{"content":"recovered"}}]}\n\ndata: [DONE]\n\n'),
        { status: 200 }
      ));
    vi.stubGlobal("fetch", fetchMock);

    const chunks: string[] = [];
    for await (const chunk of streamChatText(env, [{ role: "user", content: "hi" }])) {
      chunks.push(chunk);
    }

    expect(chunks).toEqual(["recovered"]);
    expect(fetchMock).toHaveBeenCalledTimes(2);
  });
});
