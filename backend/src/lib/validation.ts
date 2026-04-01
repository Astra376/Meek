import { AppError, assert } from "./errors";

export async function parseJson<T>(request: Request): Promise<T> {
  try {
    return (await request.json()) as T;
  } catch {
    throw new AppError(400, "INVALID_JSON", "Request body must be valid JSON.");
  }
}

export function requireString(value: unknown, field: string, maxLength = 4000): string {
  assert(typeof value === "string", 400, "VALIDATION_ERROR", `${field} must be a string.`);
  const trimmed = value.trim();
  assert(trimmed.length > 0, 400, "VALIDATION_ERROR", `${field} is required.`);
  assert(trimmed.length <= maxLength, 400, "VALIDATION_ERROR", `${field} is too long.`);
  return trimmed;
}

export function optionalString(value: unknown, field: string, maxLength = 4000): string | null {
  if (value == null || value === "") return null;
  return requireString(value, field, maxLength);
}

export function parseCursor(value: string | null): number {
  if (!value) return 0;
  const parsed = Number(value);
  if (!Number.isFinite(parsed) || parsed < 0) {
    throw new AppError(400, "INVALID_CURSOR", "Cursor must be a non-negative number.");
  }
  return parsed;
}

export function clampPageSize(value: string | null, fallback = 20, max = 50): number {
  if (!value) return fallback;
  const parsed = Number(value);
  if (!Number.isFinite(parsed) || parsed <= 0) return fallback;
  return Math.min(parsed, max);
}

