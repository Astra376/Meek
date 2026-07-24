import { describe, expect, it } from "vitest";
import type { Env, RequestContext } from "../env";
import { createSessionTokens, requireAuth } from "./auth";

const env = {
  SESSION_HMAC_SECRET: "test-session-secret"
} as Env;

function contextFor(token: string): RequestContext {
  const request = new Request("https://example.test/v1/profile/me", {
    headers: {
      Authorization: `Bearer ${token}`
    }
  });
  return {
    request,
    env,
    url: new URL(request.url),
    params: {}
  };
}

describe("requireAuth", () => {
  it("accepts a valid access token", async () => {
    const tokens = await createSessionTokens(env, "user-1");

    await expect(requireAuth(contextFor(tokens.accessToken))).resolves.toMatchObject({
      userId: "user-1",
      tokenType: "access"
    });
  });

  it("rejects a refresh token on protected application routes", async () => {
    const tokens = await createSessionTokens(env, "user-1");

    await expect(requireAuth(contextFor(tokens.refreshToken))).rejects.toMatchObject({
      status: 401,
      code: "ACCESS_TOKEN_REQUIRED"
    });
  });
});
