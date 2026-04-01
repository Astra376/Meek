export interface Env {
  DB: D1Database;
  ASSETS: R2Bucket;
  GOOGLE_WEB_CLIENT_ID: string;
  SESSION_HMAC_SECRET: string;
  OPENROUTER_API_KEY: string;
  OPENROUTER_MODEL: string;
  FAL_API_KEY: string;
  FAL_MODEL: string;
  R2_PUBLIC_BASE_URL: string;
}

export interface RequestContext {
  request: Request;
  env: Env;
  url: URL;
  params: Record<string, string>;
  user?: SessionClaims;
}

export interface SessionClaims {
  userId: string;
  tokenType: "access" | "refresh";
  exp: number;
}

export function requireConfigured(env: Env, keys: Array<keyof Env>): void {
  for (const key of keys) {
    const value = env[key];
    if (typeof value === "string" && value.trim().length === 0) {
      throw new Error(`Missing required environment variable: ${String(key)}`);
    }
  }
}

