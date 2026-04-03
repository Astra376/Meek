import type { Env, RequestContext, SessionClaims } from "../env";
import { AppError } from "./errors";

function toBase64Url(bytes: ArrayBuffer | Uint8Array): string {
  const normalized = bytes instanceof Uint8Array ? bytes : new Uint8Array(bytes);
  const base64 = btoa(String.fromCharCode(...normalized));
  return base64.replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/g, "");
}

function fromBase64Url(value: string): Uint8Array {
  const normalized = value.replace(/-/g, "+").replace(/_/g, "/");
  const padded = normalized + "=".repeat((4 - (normalized.length % 4)) % 4);
  return Uint8Array.from(atob(padded), (char) => char.charCodeAt(0));
}

function toArrayBuffer(bytes: Uint8Array): ArrayBuffer {
  const buffer = new ArrayBuffer(bytes.byteLength);
  new Uint8Array(buffer).set(bytes);
  return buffer;
}

async function importHmacKey(secret: string): Promise<CryptoKey> {
  const encoded = new TextEncoder().encode(secret);
  return crypto.subtle.importKey(
    "raw",
    toArrayBuffer(encoded),
    { name: "HMAC", hash: "SHA-256" },
    false,
    ["sign", "verify"]
  );
}

async function signPayload(secret: string, payload: object): Promise<string> {
  const header = { alg: "HS256", typ: "JWT" };
  const headerPart = toBase64Url(new TextEncoder().encode(JSON.stringify(header)));
  const payloadPart = toBase64Url(new TextEncoder().encode(JSON.stringify(payload)));
  const key = await importHmacKey(secret);
  const signature = await crypto.subtle.sign(
    "HMAC",
    key,
    new TextEncoder().encode(`${headerPart}.${payloadPart}`)
  );
  return `${headerPart}.${payloadPart}.${toBase64Url(signature)}`;
}

async function verifyToken(secret: string, token: string): Promise<Record<string, unknown>> {
  const [headerPart, payloadPart, signaturePart] = token.split(".");
  if (!headerPart || !payloadPart || !signaturePart) {
    throw new AppError(401, "INVALID_TOKEN", "Malformed session token.");
  }

  const key = await importHmacKey(secret);
  const isValid = await crypto.subtle.verify(
    "HMAC",
    key,
    toArrayBuffer(fromBase64Url(signaturePart)),
    new TextEncoder().encode(`${headerPart}.${payloadPart}`)
  );

  if (!isValid) {
    throw new AppError(401, "INVALID_TOKEN", "Session token signature is invalid.");
  }

  return JSON.parse(new TextDecoder().decode(fromBase64Url(payloadPart))) as Record<string, unknown>;
}

export async function createSessionTokens(env: Env, userId: string): Promise<{
  accessToken: string;
  refreshToken: string;
  accessExpiresAt: number;
}> {
  const nowSeconds = Math.floor(Date.now() / 1000);
  const accessExpiresAt = nowSeconds + 60 * 60;
  const refreshExpiresAt = nowSeconds + 60 * 60 * 24 * 30;

  const accessToken = await signPayload(env.SESSION_HMAC_SECRET, {
    userId,
    tokenType: "access",
    exp: accessExpiresAt
  });
  const refreshToken = await signPayload(env.SESSION_HMAC_SECRET, {
    userId,
    tokenType: "refresh",
    exp: refreshExpiresAt
  });

  return {
    accessToken,
    refreshToken,
    accessExpiresAt: accessExpiresAt * 1000
  };
}

export async function requireAuth(context: RequestContext): Promise<SessionClaims> {
  const header = context.request.headers.get("Authorization");
  if (!header?.startsWith("Bearer ")) {
    throw new AppError(401, "AUTH_REQUIRED", "A bearer token is required.");
  }

  const token = header.slice("Bearer ".length);
  const payload = await verifyToken(context.env.SESSION_HMAC_SECRET, token);
  const claims = {
    userId: String(payload.userId ?? ""),
    tokenType: payload.tokenType === "refresh" ? "refresh" : "access",
    exp: Number(payload.exp ?? 0)
  } satisfies SessionClaims;

  if (!claims.userId || claims.exp * 1000 <= Date.now()) {
    throw new AppError(401, "AUTH_EXPIRED", "Session token has expired.");
  }

  return claims;
}

export async function parseRefreshToken(env: Env, refreshToken: string): Promise<SessionClaims> {
  const payload = await verifyToken(env.SESSION_HMAC_SECRET, refreshToken);
  const claims = {
    userId: String(payload.userId ?? ""),
    tokenType: payload.tokenType === "refresh" ? "refresh" : "access",
    exp: Number(payload.exp ?? 0)
  } satisfies SessionClaims;
  if (claims.tokenType !== "refresh" || claims.exp * 1000 <= Date.now()) {
    throw new AppError(401, "AUTH_EXPIRED", "Refresh token is invalid or expired.");
  }
  return claims;
}
