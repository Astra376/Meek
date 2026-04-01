import { describe, expect, it } from "vitest";
import {
  editTranscriptMessage,
  requireLatestAssistant,
  requireRegenerationSelection,
  rewindTranscript
} from "./rules";

const transcript = [
  {
    id: "m1",
    position: 0,
    role: "user" as const,
    content: "one",
    edited: false,
    selectedRegenerationId: null,
    regenerations: []
  },
  {
    id: "m2",
    position: 1,
    role: "assistant" as const,
    content: "base",
    edited: false,
    selectedRegenerationId: "r2",
    regenerations: [
      { id: "r1", messageId: "m2", content: "first" },
      { id: "r2", messageId: "m2", content: "second" }
    ]
  }
];

describe("chat rules", () => {
  it("edits the selected regeneration target for visible assistant variants", () => {
    const target = editTranscriptMessage(transcript, "m2", "new");
    expect(target).toEqual({
      targetMessageId: "m2",
      targetRegenerationId: "r2"
    });
  });

  it("rewinds destructively to the selected message", () => {
    const rewound = rewindTranscript(
      [...transcript, { ...transcript[0], id: "m3", position: 2 }],
      "m2"
    );
    expect(rewound.map((message) => message.id)).toEqual(["m1", "m2"]);
  });

  it("allows regeneration only on the latest assistant", () => {
    const latest = requireLatestAssistant(transcript, "m2");
    expect(latest.id).toBe("m2");
  });

  it("validates regeneration selection", () => {
    expect(() => requireRegenerationSelection(transcript, "m2", "r2")).not.toThrow();
  });
});

