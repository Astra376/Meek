export interface ChatMessageRuleModel {
  id: string;
  position: number;
  role: "user" | "assistant";
  content: string;
  edited: boolean;
  selectedRegenerationId: string | null;
  regenerations: Array<{
    id: string;
    messageId: string;
    content: string;
  }>;
}

export class ChatRuleError extends Error {}

export function editTranscriptMessage(
  messages: ChatMessageRuleModel[],
  messageId: string,
  newContent: string
): { targetMessageId: string; targetRegenerationId: string | null } {
  if (!newContent.trim()) {
    throw new ChatRuleError("Message content cannot be empty.");
  }

  const target = messages.find((message) => message.id === messageId);
  if (!target) {
    throw new ChatRuleError("Message not found.");
  }

  return {
    targetMessageId: target.id,
    targetRegenerationId:
      target.role === "assistant" && target.selectedRegenerationId ? target.selectedRegenerationId : null
  };
}

export function rewindTranscript(messages: ChatMessageRuleModel[], messageId: string): ChatMessageRuleModel[] {
  const target = messages.find((message) => message.id === messageId);
  if (!target) {
    throw new ChatRuleError("Message not found.");
  }
  return messages.filter((message) => message.position <= target.position);
}

export function requireLatestAssistant(messages: ChatMessageRuleModel[], messageId: string): ChatMessageRuleModel {
  const latest = [...messages].sort((a, b) => a.position - b.position).at(-1);
  if (!latest || latest.role !== "assistant" || latest.id !== messageId) {
    throw new ChatRuleError("Only the latest assistant reply can be changed this way.");
  }
  return latest;
}

export function requireRegenerationSelection(
  messages: ChatMessageRuleModel[],
  messageId: string,
  regenerationId: string
): void {
  const latest = requireLatestAssistant(messages, messageId);
  if (!latest.regenerations.some((regeneration) => regeneration.id === regenerationId)) {
    throw new ChatRuleError("Regeneration not found.");
  }
}

