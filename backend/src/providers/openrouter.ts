import type { Env } from "../env";
import { AppError } from "../lib/errors";

interface OpenRouterMessage {
  role: "system" | "user" | "assistant";
  content: string;
}

async function throwOpenRouterError(response: Response): Promise<never> {
  let message = "Text generation failed.";
  try {
    const data = (await response.json()) as {
      error?: {
        message?: string;
      };
    };
    const providerMessage = data.error?.message?.trim();
    if (providerMessage) {
      message = providerMessage;
    }
  } catch {
    // Ignore parse failures and fall back to the generic message.
  }
  throw new AppError(502, "OPENROUTER_ERROR", message);
}

export async function generateChatText(
  env: Env,
  messages: OpenRouterMessage[]
): Promise<string> {
  const response = await fetch("https://openrouter.ai/api/v1/chat/completions", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      Authorization: `Bearer ${env.OPENROUTER_API_KEY}`
    },
    body: JSON.stringify({
      model: env.OPENROUTER_MODEL,
      messages,
      temperature: 0.8
    })
  });

  if (!response.ok) {
    await throwOpenRouterError(response);
  }

  const data = (await response.json()) as {
    choices?: Array<{ message?: { content?: string } }>;
  };

  const content = data.choices?.[0]?.message?.content?.trim();
  if (!content) {
    throw new AppError(502, "OPENROUTER_EMPTY", "The model returned an empty response.");
  }

  return content;
}

export async function* streamChatText(
  env: Env,
  messages: OpenRouterMessage[],
  signal?: AbortSignal
): AsyncGenerator<string, void, void> {
  const response = await fetch("https://openrouter.ai/api/v1/chat/completions", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      Authorization: `Bearer ${env.OPENROUTER_API_KEY}`
    },
    body: JSON.stringify({
      model: env.OPENROUTER_MODEL,
      messages,
      temperature: 0.8,
      stream: true
    }),
    signal
  });

  if (!response.ok) {
    await throwOpenRouterError(response);
  }

  const body = response.body;
  if (!body) {
    throw new AppError(502, "OPENROUTER_EMPTY", "The model returned an empty response.");
  }

  const reader = body.getReader();
  const decoder = new TextDecoder();
  let buffer = "";
  let emittedContent = false;

  try {
    while (true) {
      const { done, value } = await reader.read();
      if (done) break;

      buffer += decoder.decode(value, { stream: true });
      const events = buffer.split("\n\n");
      buffer = events.pop() ?? "";

      for (const event of events) {
        const dataLines = event
          .split("\n")
          .filter((line) => line.startsWith("data:"))
          .map((line) => line.slice(5).trim())
          .filter(Boolean);

        for (const data of dataLines) {
          if (data === "[DONE]") {
            if (!emittedContent) {
              throw new AppError(502, "OPENROUTER_EMPTY", "The model returned an empty response.");
            }
            return;
          }

          let parsed: {
            error?: { message?: string };
            choices?: Array<{ delta?: { content?: string } }>;
          };
          try {
            parsed = JSON.parse(data) as {
              error?: { message?: string };
              choices?: Array<{ delta?: { content?: string } }>;
            };
          } catch {
            continue;
          }

          if (parsed.error?.message) {
            throw new AppError(502, "OPENROUTER_ERROR", parsed.error.message);
          }

          const chunk = parsed.choices?.[0]?.delta?.content;
          if (!chunk) continue;
          emittedContent = true;
          yield chunk;
        }
      }
    }
  } finally {
    reader.releaseLock();
  }

  if (!emittedContent) {
    throw new AppError(502, "OPENROUTER_EMPTY", "The model returned an empty response.");
  }
}
