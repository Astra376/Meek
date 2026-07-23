import { describe, expect, it } from "vitest";
import { messagesForContinuation, selectRecentMessages } from "./index";

describe("selectRecentMessages", () => {
  it("keeps the newest complete messages inside the context budget", () => {
    const messages = [
      { content: "a".repeat(100), id: 1 },
      { content: "b".repeat(100), id: 2 },
      { content: "c".repeat(100), id: 3 }
    ];

    expect(selectRecentMessages(messages, 250).map((message) => message.id)).toEqual([2, 3]);
  });

  it("always keeps the latest message even when it alone exceeds the budget", () => {
    const messages = [
      { content: "old", id: 1 },
      { content: "x".repeat(1_000), id: 2 }
    ];

    expect(selectRecentMessages(messages, 100).map((message) => message.id)).toEqual([2]);
  });

  it("returns no messages for a zero budget", () => {
    expect(selectRecentMessages([{ content: "latest" }], 0)).toEqual([]);
  });
});

describe("messagesForContinuation", () => {
  const messages = [
    { role: "system" as const, content: "Stay in character." },
    { role: "user" as const, content: "Are you there?" }
  ];

  it("retries an unanswered user turn without adding another instruction", () => {
    expect(messagesForContinuation(messages, "user")).toEqual(messages);
  });

  it("adds the continue instruction after a completed assistant turn", () => {
    const result = messagesForContinuation(messages, "assistant");
    expect(result).toHaveLength(3);
    expect(result.at(-1)?.content).toContain("Continue the scene naturally");
  });
});
