import type { Env } from "../env";
import { AppError } from "../lib/errors";

interface OpenRouterMessage {
  role: "system" | "user" | "assistant";
  content: string;
}

interface CompletionOptions {
  maxTokens?: number;
  temperature?: number;
}

interface OpenRouterErrorPayload {
  error?: {
    code?: number | string;
    message?: string;
    metadata?: {
      error_type?: string;
      provider_name?: string;
    };
  };
}

class OpenRouterFailure extends Error {
  constructor(
    readonly status: number,
    message: string,
    readonly retryable: boolean,
    readonly retryAfterMs = 0
  ) {
    super(message);
  }
}

const OPENROUTER_URL = "https://openrouter.ai/api/v1/chat/completions";
const REQUEST_ATTEMPTS = 2;
const RETRYABLE_STATUSES = new Set([408, 429, 500, 502, 503, 504]);
// Keep generation shorter than the Android call timeout and the D1 run lease.
const GENERATION_TIMEOUT_MS = 100_000;
const STREAM_IDLE_TIMEOUT_MS = 35_000;

class OpenRouterTimeout extends Error {}

function generationDeadline(source?: AbortSignal) {
  const controller = new AbortController();
  const forwardAbort = () => controller.abort(source?.reason);
  if (source?.aborted) forwardAbort();
  else source?.addEventListener("abort", forwardAbort, { once: true });

  const timeout = () => controller.abort(new OpenRouterTimeout("Model generation timed out."));
  const totalTimer = setTimeout(timeout, GENERATION_TIMEOUT_MS);
  let idleTimer: ReturnType<typeof setTimeout>;
  const resetIdleTimer = () => {
    clearTimeout(idleTimer);
    idleTimer = setTimeout(timeout, STREAM_IDLE_TIMEOUT_MS);
  };
  resetIdleTimer();

  return {
    signal: controller.signal,
    resetIdleTimer,
    dispose: () => {
      clearTimeout(totalTimer);
      clearTimeout(idleTimer);
      source?.removeEventListener("abort", forwardAbort);
    }
  };
}

// Aborting fetch normally rejects pending reads, but keep the deadline effective
// even if an upstream response stalls without observing its signal.
function untilAborted<T>(pending: Promise<T>, signal?: AbortSignal): Promise<T> {
  if (!signal) return pending;
  if (signal.aborted) return Promise.reject(signal.reason);
  return new Promise<T>((resolve, reject) => {
    const onAbort = () => {
      signal.removeEventListener("abort", onAbort);
      reject(signal.reason);
    };
    signal.addEventListener("abort", onAbort, { once: true });
    pending.then(
      (value) => {
        signal.removeEventListener("abort", onAbort);
        resolve(value);
      },
      (error) => {
        signal.removeEventListener("abort", onAbort);
        reject(error);
      }
    );
  });
}

function configuredModels(env: Env): string[] {
  const candidates = [
    env.OPENROUTER_MODEL,
    ...(env.OPENROUTER_FALLBACK_MODELS ?? "").split(",")
  ];
  return [...new Set(candidates.filter((model): model is string => typeof model === "string")
    .map((model) => model.trim()).filter(Boolean))];
}

function modelSelection(env: Env): { model: string } | { models: string[] } {
  const models = configuredModels(env);
  if (!models.length) {
    throw new AppError(503, "MODEL_CONFIGURATION_ERROR", "The AI service is not configured. Please try again later.");
  }
  if (models.length > 1) return { models };
  return { model: models[0] };
}

function requestHeaders(env: Env): Record<string, string> {
  if (!env.OPENROUTER_API_KEY?.trim()) {
    throw new AppError(503, "MODEL_CONFIGURATION_ERROR", "The AI service is not configured. Please try again later.");
  }
  return {
    "Content-Type": "application/json",
    Authorization: `Bearer ${env.OPENROUTER_API_KEY}`,
    "HTTP-Referer": "https://meek.chat",
    "X-Title": "Meek"
  };
}

function retryAfterMs(response: Response): number {
  const value = response.headers.get("Retry-After")?.trim();
  if (!value) return 0;
  const seconds = Number(value);
  if (Number.isFinite(seconds)) return Math.max(0, seconds * 1_000);
  const date = Date.parse(value);
  return Number.isFinite(date) ? Math.max(0, date - Date.now()) : 0;
}

async function parseFailure(response: Response, signal?: AbortSignal): Promise<OpenRouterFailure> {
  let providerMessage = "Text generation failed.";
  let providerCode: number | string | undefined;
  let errorType: string | undefined;
  let providerName: string | undefined;
  try {
    const data = (await untilAborted(response.json(), signal)) as OpenRouterErrorPayload;
    providerMessage = data.error?.message?.trim() || providerMessage;
    providerCode = data.error?.code;
    errorType = data.error?.metadata?.error_type;
    providerName = data.error?.metadata?.provider_name;
  } catch {
    // The HTTP status is still enough to classify the failure.
  }

  console.warn("OpenRouter request failed", {
    status: response.status,
    providerCode,
    errorType,
    providerName
  });
  return new OpenRouterFailure(
    response.status,
    providerMessage,
    RETRYABLE_STATUSES.has(response.status),
    retryAfterMs(response)
  );
}

function publicError(error: unknown): AppError {
  if (error instanceof AppError) return error;
  if (error instanceof OpenRouterTimeout) {
    return new AppError(504, "MODEL_PROVIDER_TIMEOUT", "The model took too long to reply. Please try again.");
  }
  if (error instanceof OpenRouterFailure) {
    const normalized = error.message.toLowerCase();
    if (
      error.status === 400 &&
      (normalized.includes("context") || normalized.includes("token"))
    ) {
      return new AppError(
        400,
        "MODEL_CONTEXT_LIMIT",
        "This conversation is too long for the selected model. Its saved memory is intact."
      );
    }
    if (error.status === 401 || error.status === 402 || error.status === 403) {
      return new AppError(
        503,
        "MODEL_CONFIGURATION_ERROR",
        "The AI service is temporarily unavailable. Please try again later."
      );
    }
  }
  return new AppError(
    503,
    "MODEL_PROVIDER_UNAVAILABLE",
    "The model provider is temporarily unavailable. Please try again."
  );
}

async function waitBeforeRetry(attempt: number, requestedDelay: number, signal?: AbortSignal) {
  const delayMs = Math.min(Math.max(requestedDelay, 250 * 2 ** attempt), 2_000);
  await new Promise<void>((resolve, reject) => {
    if (signal?.aborted) {
      reject(signal.reason ?? new DOMException("Aborted", "AbortError"));
      return;
    }
    const onAbort = () => {
      clearTimeout(timer);
      reject(signal?.reason ?? new DOMException("Aborted", "AbortError"));
    };
    const timer = setTimeout(() => {
      signal?.removeEventListener("abort", onAbort);
      resolve();
    }, delayMs);
    signal?.addEventListener("abort", onAbort, { once: true });
  });
}

async function requestOpenRouter(
  env: Env,
  body: Record<string, unknown>,
  signal?: AbortSignal
): Promise<Response> {
  let lastFailure: unknown;
  for (let attempt = 0; attempt < REQUEST_ATTEMPTS; attempt += 1) {
    let retryDelay = 0;
    try {
      const response = await untilAborted(fetch(OPENROUTER_URL, {
        method: "POST",
        headers: requestHeaders(env),
        body: JSON.stringify(body),
        signal
      }), signal);
      if (response.ok) return response;

      const failure = await parseFailure(response, signal);
      lastFailure = failure;
      if (!failure.retryable || attempt === REQUEST_ATTEMPTS - 1) throw failure;
      retryDelay = failure.retryAfterMs;
    } catch (error) {
      if (signal?.aborted) throw signal.reason ?? error;
      lastFailure = error;
      if (error instanceof AppError) throw error;
      if (error instanceof OpenRouterFailure && !error.retryable) throw error;
      if (attempt === REQUEST_ATTEMPTS - 1) throw error;
    }
    await waitBeforeRetry(attempt, retryDelay, signal);
  }
  throw lastFailure;
}

async function* readCompletionStream(
  response: Response,
  signal?: AbortSignal,
  onActivity?: () => void
): AsyncGenerator<string, void, void> {
  const body = response.body;
  if (!body) {
    throw new OpenRouterFailure(502, "The model returned an empty response.", true);
  }

  const reader = body.getReader();
  const decoder = new TextDecoder();
  let buffer = "";
  let emittedContent = false;

  function parseEvent(event: string): string[] {
    const chunks: string[] = [];
    const dataLines = event
      .split("\n")
      .filter((line) => line.startsWith("data:"))
      .map((line) => line.slice(5).trim())
      .filter(Boolean);

    for (const data of dataLines) {
      onActivity?.();
      if (data === "[DONE]") continue;
      let parsed: OpenRouterErrorPayload & {
        choices?: Array<{ delta?: { content?: string } }>;
      };
      try {
        parsed = JSON.parse(data) as typeof parsed;
      } catch {
        continue;
      }
      if (parsed.error) {
        throw new OpenRouterFailure(
          Number(parsed.error.code) || 502,
          parsed.error.message || "The model provider ended the response.",
          !emittedContent
        );
      }
      const chunk = parsed.choices?.[0]?.delta?.content;
      if (chunk) {
        emittedContent = true;
        chunks.push(chunk);
      }
    }
    return chunks;
  }

  try {
    while (true) {
      const { done, value } = await untilAborted(reader.read(), signal);
      if (signal?.aborted) throw signal.reason;
      if (done) break;
      buffer += decoder.decode(value, { stream: true });
      buffer = normalizeSseNewlines(buffer);
      const events = buffer.split("\n\n");
      buffer = events.pop() ?? "";
      for (const event of events) {
        for (const chunk of parseEvent(event)) yield chunk;
      }
    }
    buffer += decoder.decode();
    if (buffer.trim()) {
      for (const chunk of parseEvent(normalizeSseNewlines(buffer))) yield chunk;
    }
  } finally {
    if (signal?.aborted) {
      void reader.cancel().catch(() => {});
      // A pending read in a non-cooperative stream can retain the lock until
      // its cancel settles. The Response is no longer used at this point.
      try { reader.releaseLock(); } catch { /* Pending read. */ }
    } else {
      reader.releaseLock();
    }
  }

  if (!emittedContent) {
    throw new OpenRouterFailure(502, "The model returned an empty response.", true);
  }
}

export async function* streamChatText(
  env: Env,
  messages: OpenRouterMessage[],
  signal?: AbortSignal
): AsyncGenerator<string, void, void> {
  let emittedAnyContent = false;
  const deadline = generationDeadline(signal);
  try {
    for (let streamAttempt = 0; streamAttempt < 2; streamAttempt += 1) {
      try {
        const response = await requestOpenRouter(env, {
          ...modelSelection(env),
          messages,
          max_tokens: 1000,
          temperature: 0.8,
          stream: true,
          provider: { allow_fallbacks: true }
        }, deadline.signal);
        for await (const chunk of readCompletionStream(response, deadline.signal, deadline.resetIdleTimer)) {
          emittedAnyContent = true;
          yield chunk;
        }
        return;
      } catch (error) {
        const canRestart =
          !emittedAnyContent &&
          streamAttempt === 0 &&
          !deadline.signal.aborted &&
          (!(error instanceof OpenRouterFailure) || error.retryable);
        if (!canRestart) throw error;
        await waitBeforeRetry(streamAttempt, 0, deadline.signal);
      }
    }
  } catch (error) {
    if (signal?.aborted) throw error;
    throw publicError(deadline.signal.aborted ? deadline.signal.reason : error);
  } finally {
    deadline.dispose();
  }
}

export async function completeChatText(
  env: Env,
  messages: OpenRouterMessage[],
  options: CompletionOptions = {}
): Promise<string> {
  try {
    for (let completionAttempt = 0; completionAttempt < 2; completionAttempt += 1) {
      try {
        const response = await requestOpenRouter(env, {
          ...modelSelection(env),
          messages,
          max_tokens: options.maxTokens ?? 2000,
          temperature: options.temperature ?? 0.2,
          stream: false,
          provider: { allow_fallbacks: true }
        });
        const data = (await response.json()) as OpenRouterErrorPayload & {
          choices?: Array<{ message?: { content?: string } }>;
        };
        if (data.error) {
          throw new OpenRouterFailure(
            Number(data.error.code) || 502,
            data.error.message || "Text generation failed.",
            true
          );
        }
        const content = data.choices?.[0]?.message?.content?.trim();
        if (!content) {
          throw new OpenRouterFailure(502, "The model returned an empty response.", true);
        }
        return content;
      } catch (error) {
        const canRetry =
          completionAttempt === 0 &&
          (!(error instanceof OpenRouterFailure) || error.retryable);
        if (!canRetry) throw error;
        await waitBeforeRetry(completionAttempt, 0);
      }
    }
    throw new OpenRouterFailure(502, "The model returned an empty response.", true);
  } catch (error) {
    throw publicError(error);
  }
}

function normalizeSseNewlines(value: string): string {
  return value.replace(/\r\n/g, "\n").replace(/\r/g, "\n");
}
