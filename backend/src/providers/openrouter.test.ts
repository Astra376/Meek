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
    vi.useRealTimers();
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

  it("reports a missing model configuration before attempting a provider request", async () => {
    const fetchMock = vi.fn();
    vi.stubGlobal("fetch", fetchMock);
    const missingKey = { ...env, OPENROUTER_API_KEY: "" };
    const reply = (async () => {
      for await (const _chunk of streamChatText(missingKey, [{ role: "user", content: "hi" }])) {
        // No model request should start.
      }
    })();

    await expect(reply).rejects.toMatchObject({ code: "MODEL_CONFIGURATION_ERROR" });
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("retries a transient provider response before streaming", async () => {
    vi.useFakeTimers();
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
    const reply = (async () => {
      for await (const chunk of streamChatText(env, [{ role: "user", content: "hi" }])) {
        chunks.push(chunk);
      }
    })();
    await vi.advanceTimersByTimeAsync(250);
    await reply;

    expect(chunks).toEqual(["recovered"]);
    expect(fetchMock).toHaveBeenCalledTimes(2);
  });

  it("ends a provider request that never returns and permits the next reply", async () => {
    vi.useFakeTimers();
    const fetchMock = vi.fn()
      .mockImplementationOnce(() => new Promise<Response>(() => {}))
      .mockResolvedValueOnce(new Response(
        streamFromText('data: {"choices":[{"delta":{"content":"ready"}}]}\n\ndata: [DONE]\n\n'),
        { status: 200 }
      ));
    vi.stubGlobal("fetch", fetchMock);

    const firstReply = (async () => {
      for await (const _chunk of streamChatText(env, [{ role: "user", content: "first" }])) {
        // The first provider call never returns.
      }
    })();
    const timedOut = expect(firstReply).rejects.toMatchObject({ code: "MODEL_PROVIDER_TIMEOUT" });
    await vi.advanceTimersByTimeAsync(35_000);
    await timedOut;

    const chunks: string[] = [];
    for await (const chunk of streamChatText(env, [{ role: "user", content: "second" }])) {
      chunks.push(chunk);
    }
    expect(chunks).toEqual(["ready"]);
    expect(fetchMock).toHaveBeenCalledTimes(2);
  });

  it("stops a stream that silently stalls after its first token", async () => {
    vi.useFakeTimers();
    vi.stubGlobal("fetch", vi.fn(async () => new Response(
      new ReadableStream<Uint8Array>({
        start(controller) {
          controller.enqueue(new TextEncoder().encode(
            'data: {"choices":[{"delta":{"content":"partial"}}]}\n\n'
          ));
        }
      }),
      { status: 200 }
    )));

    const chunks: string[] = [];
    const reply = (async () => {
      for await (const chunk of streamChatText(env, [{ role: "user", content: "hi" }])) {
        chunks.push(chunk);
      }
    })();
    const timedOut = expect(reply).rejects.toMatchObject({ code: "MODEL_PROVIDER_TIMEOUT" });
    await vi.advanceTimersByTimeAsync(35_000);
    await timedOut;
    expect(chunks).toEqual(["partial"]);
  });

  it("does not treat active reasoning chunks as a stalled provider", async () => {
    vi.useFakeTimers();
    let controller!: ReadableStreamDefaultController<Uint8Array>;
    vi.stubGlobal("fetch", vi.fn(async () => new Response(
      new ReadableStream<Uint8Array>({ start(streamController) { controller = streamController; } }),
      { status: 200 }
    )));

    const chunks: string[] = [];
    const reply = (async () => {
      for await (const chunk of streamChatText(env, [{ role: "user", content: "hi" }])) {
        chunks.push(chunk);
      }
    })();
    await vi.advanceTimersByTimeAsync(30_000);
    controller.enqueue(new TextEncoder().encode('data: {"choices":[{"delta":{"reasoning":"thinking"}}]}\n\n'));
    await vi.advanceTimersByTimeAsync(30_000);
    controller.enqueue(new TextEncoder().encode('data: {"choices":[{"delta":{"content":"answer"}}]}\n\ndata: [DONE]\n\n'));
    controller.close();
    await reply;
    expect(chunks).toEqual(["answer"]);
  });
});
