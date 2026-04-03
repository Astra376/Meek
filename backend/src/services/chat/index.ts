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
import { generateChatText, streamChatText } from "../../providers/openrouter";
import { editTranscriptMessage, requireLatestAssistant, requireRegenerationSelection, rewindTranscript } from "./rules";

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

async function buildAssistantContext(context: RequestContext, conversationId: string, untilPosition?: number) {
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
    messages
  };
}

function sseEvent(payload: object): Uint8Array {
  return new TextEncoder().encode(`data: ${JSON.stringify(payload)}\n\n`);
}

async function streamAssistantReply(
  context: RequestContext,
  messages: Array<{ role: "system" | "user" | "assistant"; content: string }>,
  onChunk: (chunk: string) => void,
  signal: AbortSignal
): Promise<string> {
  let fullText = "";
  let emittedChunk = false;

  try {
    for await (const chunk of streamChatText(context.env, messages, signal)) {
      emittedChunk = true;
      fullText += chunk;
      onChunk(chunk);
    }
  } catch (error) {
    if (emittedChunk) {
      throw error;
    }

    const fallbackText = await generateChatText(context.env, messages);
    fullText = fallbackText;
    onChunk(fallbackText);
  }

  const finalText = fullText.trim();
  if (!finalText) {
    throw new AppError(502, "OPENROUTER_EMPTY", "The model returned an empty response.");
  }

  return finalText;
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
  const userMessageId = createId("message");
  await insertMessage(context.env, {
    id: userMessageId,
    conversation_id: conversationId,
    position: existing.length,
    role: "user",
    content,
    edited: 0,
    created_at: now,
    updated_at: now,
    selected_regeneration_id: null
  });
  const { character, messages } = await buildAssistantContext(context, conversationId);
  const abortController = new AbortController();

  const stream = new ReadableStream({
    async start(controller) {
      try {
        const finalText = await streamAssistantReply(
          context,
          messages,
          (chunk) => {
            controller.enqueue(sseEvent({ type: "chunk", text: chunk }));
          },
          abortController.signal
        );

        const assistantNow = Date.now();
        await insertMessage(context.env, {
          id: createId("message"),
          conversation_id: conversationId,
          position: existing.length + 1,
          role: "assistant",
          content: finalText,
          edited: 0,
          created_at: assistantNow,
          updated_at: assistantNow,
          selected_regeneration_id: null
        });
        await updateConversationActivity(context.env, conversationId, assistantNow);
        await incrementCharacterActivity(context.env, character.id, assistantNow);
        controller.enqueue(sseEvent({ type: "done" }));
        controller.close();
      } catch (error) {
        abortController.abort();
        controller.error(error);
      }
    },
    cancel() {
      abortController.abort();
    }
  });

  return new Response(stream, {
    headers: {
      "Content-Type": "text/event-stream; charset=utf-8",
      "Cache-Control": "no-store"
    }
  });
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

  const regenerationId = createId("regen");
  const { messages } = await buildAssistantContext(context, message.conversation_id, latest.position);
  const abortController = new AbortController();

  const stream = new ReadableStream({
    async start(controller) {
      try {
        const finalText = await streamAssistantReply(
          context,
          messages,
          (chunk) => {
            controller.enqueue(sseEvent({ type: "chunk", text: chunk }));
          },
          abortController.signal
        );

        await insertRegeneration(context.env, {
          id: regenerationId,
          message_id: latest.id,
          content: finalText,
          created_at: Date.now()
        });
        await updateMessageSelection(context.env, {
          messageId: latest.id,
          selectedRegenerationId: regenerationId,
          updatedAt: Date.now()
        });

        controller.enqueue(sseEvent({ type: "done" }));
        controller.close();
      } catch (error) {
        abortController.abort();
        controller.error(error);
      }
    },
    cancel() {
      abortController.abort();
    }
  });

  return new Response(stream, {
    headers: {
      "Content-Type": "text/event-stream; charset=utf-8",
      "Cache-Control": "no-store"
    }
  });
}
