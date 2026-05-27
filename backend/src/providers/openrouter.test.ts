import { afterEach, describe, expect, it, vi } from "vitest";
import { streamChatText } from "./openrouter";
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
});
