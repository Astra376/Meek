import type { Env } from "../env";
import { AppError } from "../lib/errors";

interface OpenRouterMessage {
  role: "system" | "user" | "assistant";
  content: string;
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
    throw new AppError(502, "OPENROUTER_ERROR", "Text generation failed.");
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

