import { describe, expect, it } from "vitest";
import { matchPath } from "./index";

describe("matchPath", () => {
  it("captures canonical encoded asset keys", () => {
    expect(
      matchPath(
        "/v1/assets/*key",
        "/v1/assets/chat-backgrounds%2Fuser_1%2Fbackground_1.jpg"
      )
    ).toEqual({
      key: "chat-backgrounds/user_1/background_1.jpg"
    });
  });

  it("captures legacy asset keys containing literal slashes", () => {
    expect(
      matchPath(
        "/v1/assets/*key",
        "/v1/assets/chat-backgrounds/user_1/background_1.jpg"
      )
    ).toEqual({
      key: "chat-backgrounds/user_1/background_1.jpg"
    });
  });

  it("does not make ordinary route parameters greedy", () => {
    expect(matchPath("/v1/conversations/:id", "/v1/conversations/one/messages")).toBeNull();
  });
});
