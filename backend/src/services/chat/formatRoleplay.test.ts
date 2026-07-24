import { describe, expect, it } from "vitest";
import { formatRoleplayMessage } from "./formatRoleplay";

describe("formatRoleplayMessage", () => {
  it("separates adjacent actions and speech returned as one block", () => {
    expect(
      formatRoleplayMessage(
        '*Mara looks up from the map.* "You are late." *She folds her arms.* "Well?"'
      )
    ).toBe(
      [
        "*Mara looks up from the map.*",
        '"You are late."',
        "*She folds her arms.*",
        '"Well?"'
      ].join("\n\n")
    );
  });

  it("repairs single newlines and supports curly dialogue quotes", () => {
    expect(
      formatRoleplayMessage("*Mara shuts the door.*\n“Not tonight.”\n*The lock clicks.*")
    ).toBe("*Mara shuts the door.*\n\n“Not tonight.”\n\n*The lock clicks.*");
  });

  it("keeps prose and existing paragraph content intact", () => {
    const message = "The rain keeps falling.\n\nNobody moves.";
    expect(formatRoleplayMessage(message)).toBe(message);
  });

  it("supports bold and bold-italic Markdown blocks without stripping markup", () => {
    expect(formatRoleplayMessage('**Careful.** ***She steps back.*** "Stay there."')).toBe(
      '**Careful.**\n\n***She steps back.***\n\n"Stay there."'
    );
  });
});
