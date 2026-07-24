import { describe, expect, it } from "vitest";
import {
  LONG_TERM_MEMORY_LIMIT,
  appendLongTermMemory,
  composeCharacterSystemPrompt,
  formatCharacterMemoryPrompt,
  invalidateAutomaticEntriesFrom,
  selectShortTermHorizon,
  withoutAutomaticEntries
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

  it("rebuilds automatic events without deleting user-maintained memory", () => {
    const longTerm = [
      "- The user manually noted Mara fears deep water.",
      "- Mara found the crown in the abandoned branch."
    ].join("\n");

    const preserved = withoutAutomaticEntries(longTerm, [
      { text: "Mara found the crown in the abandoned branch.", sourcePosition: 18 }
    ]);

    expect(preserved).toContain("Mara fears deep water.");
    expect(preserved).not.toContain("found the crown");
  });

  it("invalidates only automatic events from a corrected branch", () => {
    const longTerm = [
      "- The user manually noted Mara fears deep water.",
      "- Mara found the silver key.",
      "- Mara gave the key to Rowan."
    ].join("\n");
    const result = invalidateAutomaticEntriesFrom(
      longTerm,
      [
        { text: "Mara found the silver key.", sourcePosition: 12 },
        { text: "Mara gave the key to Rowan.", sourcePosition: 24 }
      ],
      20
    );

    expect(result.longTerm).toContain("Mara fears deep water.");
    expect(result.longTerm).toContain("Mara found the silver key.");
    expect(result.longTerm).not.toContain("gave the key");
    expect(result.retainedEntries).toEqual([
      { text: "Mara found the silver key.", sourcePosition: 12 }
    ]);
  });

  it("uses an older rolling horizon instead of duplicating immediate chat context", () => {
    const transcript = Array.from({ length: 64 }, (_, position) => ({
      position,
      role: position % 2 === 0 ? "user" as const : "assistant" as const,
      content: `Message ${position}`
    }));

    const horizon = selectShortTermHorizon(transcript);

    expect(horizon).toHaveLength(48);
    expect(horizon[0].position).toBe(8);
    expect(horizon.at(-1)?.position).toBe(55);
    expect(horizon.some((message) => message.position >= 56)).toBe(false);
  });

  it("leaves short-term memory empty while the whole conversation is immediate context", () => {
    const transcript = Array.from({ length: 8 }, (_, position) => ({
      position,
      role: "user" as const,
      content: `Message ${position}`
    }));

    expect(selectShortTermHorizon(transcript)).toEqual([]);
  });
});
