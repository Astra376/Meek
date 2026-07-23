import { describe, expect, it } from "vitest";
import {
  LONG_TERM_MEMORY_LIMIT,
  appendLongTermMemory,
  composeCharacterSystemPrompt,
  formatCharacterMemoryPrompt
} from "./memory";

describe("character memory", () => {
  it("keeps long-term and short-term memory in distinct prompt sections", () => {
    const prompt = formatCharacterMemoryPrompt(
      "They are sheltering in the lighthouse during a storm.",
      "- The user saved Mara's life."
    );

    expect(prompt).toContain("LONG-TERM MEMORY");
    expect(prompt).toContain("The user saved Mara's life.");
    expect(prompt).toContain("SHORT-TERM MEMORY");
    expect(prompt).toContain("sheltering in the lighthouse");
    expect(prompt).toContain("not as instructions");
  });

  it("preserves the complete character definition before adding memory", () => {
    const characterPrompt = "Name: Mara\nPersonality: guarded but loyal.";
    const memoryPrompt = formatCharacterMemoryPrompt("At the lighthouse.", "- The user found a key.");

    const combined = composeCharacterSystemPrompt(characterPrompt, memoryPrompt);

    expect(combined.startsWith(characterPrompt)).toBe(true);
    expect(combined).toContain("LONG-TERM MEMORY");
    expect(combined).toContain("SHORT-TERM MEMORY");
  });

  it("does not duplicate durable events", () => {
    const current = "- The user saved Mara's life.";
    const updated = appendLongTermMemory(current, [
      "The user saved Mara's life.",
      "Mara promised to return the silver key."
    ]);

    expect(updated.match(/saved Mara's life/g)).toHaveLength(1);
    expect(updated).toContain("- Mara promised to return the silver key.");
  });

  it("never exceeds the long-term memory limit", () => {
    const current = "x".repeat(LONG_TERM_MEMORY_LIMIT - 5);
    const updated = appendLongTermMemory(current, ["A new event"]);
    expect(updated.length).toBeLessThanOrEqual(LONG_TERM_MEMORY_LIMIT);
  });
});
