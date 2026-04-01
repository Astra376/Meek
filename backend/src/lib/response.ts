import { AppError } from "./errors";

const defaultHeaders = {
  "Content-Type": "application/json; charset=utf-8",
  "Cache-Control": "no-store",
  "Access-Control-Allow-Origin": "*"
};

export function json(data: unknown, init: ResponseInit = {}): Response {
  return new Response(JSON.stringify(data), {
    ...init,
    headers: {
      ...defaultHeaders,
      ...(init.headers ?? {})
    }
  });
}

export function noContent(): Response {
  return new Response(null, { status: 204 });
}

export function handleError(error: unknown): Response {
  if (error instanceof AppError) {
    return json(
      {
        code: error.code,
        message: error.message
      },
      { status: error.status }
    );
  }

  console.error("Unhandled worker error", error);
  return json(
    {
      code: "INTERNAL_ERROR",
      message: "Something went wrong."
    },
    { status: 500 }
  );
}
