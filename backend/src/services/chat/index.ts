import type { RequestContext } from "../../env";
import { getCharacterById, incrementCharacterActivity } from "../../db/queries/characters";
import {
  deleteMessagesAfter,
  getConversationById,
  getMessageById,
  insertMessage,
  insertRegeneration,
  listMessages,
  listRegenerationsForConversation,
  updateConversationActivity,
  updateMessageContent,
  updateMessageSelection,
  updateRegenerationContent
} from "../../db/queries/conversations";
import { AppError, forbidden } from "../../lib/errors";
import { createId } from "../../lib/ids";
import { generateChatText } from "../../providers/openrouter";
import {
  ChatRuleError,
  editTranscriptMessage,
  requireLatestAssistant,
  requireRegenerationSelection,
  rewindTranscript
} from "./rules";

async function loadTranscript(context: RequestContext, conversationId: string) {
  const messages = await listMessages(context.env, conversationId);
  const regenerations = await listRegenerationsForConversation(context.env, conversationId);
  const grouped = new Map<string, typeof regenerations>();
  for (const regeneration of regenerations) {
    const list = grouped.get(regeneration.message_id) ?? [];
    list.push(regeneration);
    grouped.set(regeneration.message_id, list);
  }

  return messages.map((message) => ({
    id: message.id,
    position: message.position,
    role: message.role,
    content: message.content,
    edited: Boolean(message.edited),
    selectedRegenerationId: message.selected_regeneration_id,
    regenerations: (grouped.get(message.id) ?? []).map((regeneration) => ({
      id: regeneration.id,
      messageId: regeneration.message_id,
      content: regeneration.content
    }))
  }));
}

function visibleContent(message: Awaited<ReturnType<typeof loadTranscript>>[number]): string {
  return (
    message.regenerations.find((regeneration) => regeneration.id === message.selectedRegenerationId)?.content ??
    message.content
  );
}

async function requireOwnedConversation(context: RequestContext, conversationId: string) {
  const conversation = await getConversationById(context.env, conversationId);
  if (!conversation) {
    throw new AppError(404, "CONVERSATION_NOT_FOUND", "Conversation not found.");
  }
  if (conversation.owner_user_id !== context.user!.userId) {
    forbidden("Conversations are private to their owner.");
  }
  return conversation;
}

async function generateAssistantText(context: RequestContext, conversationId: string, untilPosition?: number) {
  const conversation = await requireOwnedConversation(context, conversationId);
  const character = await getCharacterById(context.env, context.user!.userId, conversation.character_id);
  if (!character) {
    throw new AppError(404, "CHARACTER_NOT_FOUND", "Character not found.");
  }
  const transcript = await loadTranscript(context, conversationId);
  const visibleTranscript = transcript
    .filter((message) => untilPosition == null || message.position < untilPosition)
    .map((message) => ({
      role: message.role,
      content: visibleContent(message)
    }));
  const messages = [
    { role: "system" as const, content: character.system_prompt },
    ...visibleTranscript
  ];
  return {
    conversation,
    character,
    text: await generateChatText(context.env, messages)
  };
}

function streamTextChunks(text: string): Response {
  const encoder = new TextEncoder();
  const stream = new ReadableStream({
    start(controller) {
      const chunks = text.match(/.{1,48}/g) ?? [text];
      for (const chunk of chunks) {
        controller.enqueue(encoder.encode(`data: ${JSON.stringify({ type: "chunk", text: chunk })}\n\n`));
      }
      controller.enqueue(encoder.encode(`data: ${JSON.stringify({ type: "done" })}\n\n`));
      controller.close();
    }
  });

  return new Response(stream, {
    headers: {
      "Content-Type": "text/event-stream; charset=utf-8",
      "Cache-Control": "no-store"
    }
  });
}

export async function editMessage(context: RequestContext, messageId: string, newContent: string) {
  const message = await getMessageById(context.env, messageId);
  if (!message) {
    throw new AppError(404, "MESSAGE_NOT_FOUND", "Message not found.");
  }
  await requireOwnedConversation(context, message.conversation_id);
  const transcript = await loadTranscript(context, message.conversation_id);
  let target: { targetMessageId: string; targetRegenerationId: string | null };
  try {
    target = editTranscriptMessage(transcript, messageId, newContent);
  } catch (error) {
    throw new AppError(400, "CHAT_RULE_ERROR", (error as Error).message);
  }

  const now = Date.now();
  if (target.targetRegenerationId) {
    await updateRegenerationContent(context.env, target.targetRegenerationId, newContent.trim());
    await updateMessageSelection(context.env, {
      messageId: target.targetMessageId,
      selectedRegenerationId: target.targetRegenerationId,
      updatedAt: now,
      edited: true
    });
  } else {
    await updateMessageContent(context.env, {
      messageId: target.targetMessageId,
      content: newContent.trim(),
      edited: true,
      updatedAt: now
    });
  }
}

export async function rewindConversation(context: RequestContext, messageId: string) {
  const message = await getMessageById(context.env, messageId);
  if (!message) {
    throw new AppError(404, "MESSAGE_NOT_FOUND", "Message not found.");
  }
  await requireOwnedConversation(context, message.conversation_id);
  const transcript = await loadTranscript(context, message.conversation_id);
  let remaining;
  try {
    remaining = rewindTranscript(transcript, messageId);
  } catch (error) {
    throw new AppError(400, "CHAT_RULE_ERROR", (error as Error).message);
  }
  const last = remaining.at(-1);
  await deleteMessagesAfter(context.env, message.conversation_id, last?.position ?? -1);
  await updateConversationActivity(context.env, message.conversation_id, Date.now());
}

export async function selectRegeneration(context: RequestContext, messageId: string, regenerationId: string) {
  const message = await getMessageById(context.env, messageId);
  if (!message) {
    throw new AppError(404, "MESSAGE_NOT_FOUND", "Message not found.");
  }
  await requireOwnedConversation(context, message.conversation_id);
  const transcript = await loadTranscript(context, message.conversation_id);
  try {
    requireRegenerationSelection(transcript, messageId, regenerationId);
  } catch (error) {
    throw new AppError(400, "CHAT_RULE_ERROR", (error as Error).message);
  }
  await updateMessageSelection(context.env, {
    messageId,
    selectedRegenerationId: regenerationId,
    updatedAt: Date.now()
  });
}

export async function sendMessageAndStream(context: RequestContext, conversationId: string, content: string): Promise<Response> {
  const conversation = await requireOwnedConversation(context, conversationId);
  const existing = await listMessages(context.env, conversationId);
  const now = Date.now();
  await insertMessage(context.env, {
    id: createId("message"),
    conversation_id: conversationId,
    position: existing.length,
    role: "user",
    content,
    edited: 0,
    created_at: now,
    updated_at: now,
    selected_regeneration_id: null
  });

  const { text, character } = await generateAssistantText(context, conversationId);
  const assistantNow = Date.now();
  await insertMessage(context.env, {
    id: createId("message"),
    conversation_id: conversationId,
    position: existing.length + 1,
    role: "assistant",
    content: text,
    edited: 0,
    created_at: assistantNow,
    updated_at: assistantNow,
    selected_regeneration_id: null
  });
  await updateConversationActivity(context.env, conversationId, assistantNow);
  await incrementCharacterActivity(context.env, character.id, assistantNow);
  return streamTextChunks(text);
}

export async function regenerateLatestAssistantAndStream(
  context: RequestContext,
  messageId: string
): Promise<Response> {
  const message = await getMessageById(context.env, messageId);
  if (!message) {
    throw new AppError(404, "MESSAGE_NOT_FOUND", "Message not found.");
  }
  await requireOwnedConversation(context, message.conversation_id);
  const transcript = await loadTranscript(context, message.conversation_id);
  let latest;
  try {
    latest = requireLatestAssistant(transcript, messageId);
  } catch (error) {
    throw new AppError(400, "CHAT_RULE_ERROR", (error as Error).message);
  }

  const generated = await generateAssistantText(context, message.conversation_id, latest.position);
  const regenerationId = createId("regen");
  await insertRegeneration(context.env, {
    id: regenerationId,
    message_id: latest.id,
    content: generated.text,
    created_at: Date.now()
  });
  await updateMessageSelection(context.env, {
    messageId: latest.id,
    selectedRegenerationId: regenerationId,
    updatedAt: Date.now()
  });

  return streamTextChunks(generated.text);
}

