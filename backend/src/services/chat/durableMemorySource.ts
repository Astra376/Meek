type EvidenceMessage = { position: number; content: string };
function normalizeMemoryQuote(value: unknown): string {
  return typeof value === "string" ? value.normalize("NFKC").replace(/[‘’]/g, "'").replace(/[“”]/g, '"').replace(/\s+/g, " ").trim().toLowerCase() : "";
}

export function resolveMemorySource(candidate: unknown, sources: EvidenceMessage[]): number | undefined {
  if (!candidate || typeof candidate !== "object") return;
  const note = candidate as {sourceQuote?: unknown; sourcePosition?: unknown};
  const quote = normalizeMemoryQuote(note.sourceQuote);
  if (quote.length < 4) return;
  const matches = sources.filter(source => normalizeMemoryQuote(source.content).includes(quote));
  const position = typeof note.sourcePosition === "number" ? note.sourcePosition :
    typeof note.sourcePosition === "string" && /^\d+$/.test(note.sourcePosition.trim()) ? Number(note.sourcePosition) : undefined;
  const exact = matches.find(source => source.position === position);
  return exact?.position ?? (matches.length === 1 ? matches[0].position : undefined);
}
