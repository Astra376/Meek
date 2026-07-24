// Models sometimes ignore the requested paragraph layout and return adjacent
// Markdown action and quoted speech blocks separated by only spaces. Normalize
// those explicit boundaries in code so persisted replies remain readable.
const ROLEPLAY_BLOCK_BOUNDARY =
  /((?<!\*)\*{1,3}(?!\*)|["”])[ \t]*(?:\r?\n[ \t]*)*(?=(?:(?<!\*)\*{1,3}(?!\*)|["“]))/g;

export function formatRoleplayMessage(value: string): string {
  return value
    .replace(/\r\n?/g, "\n")
    .replace(ROLEPLAY_BLOCK_BOUNDARY, "$1\n\n")
    .trim();
}
