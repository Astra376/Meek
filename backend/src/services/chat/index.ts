import type { RequestContext } from "../../env";
import { ensureConversationStreamingSchema } from "../../db/ensureConversationStreamingSchema";
import { getCharacterById, incrementCharacterActivity } from "../../db/queries/characters";
import {
  claimConversationRun,
  deleteMessagesAfter,
  getConversationById,
  getConversationSummaryById,
  getMessageById,
  insertMessage,
  insertRegeneration,
  listMessages,
  listRegenerationsForConversation,
  releaseConversationRun,
  updateConversationActivity,
  updateMessageContent,
  updateMessageSelection,
  updateRegenerationContent
} from "../../db/queries/conversations";
import { AppError, forbidden } from "../../lib/errors";
import { createId } from "../../lib/ids";
import { generateChatText, streamChatText } from "../../providers/openrouter";
import { editTranscriptMessage, requireLatestAssistant, requireRegenerationSelection, rewindTranscript } from "./rules";

const RUN_LOCK_WINDOW_MS = 2 * 60 * 1000;

type TranscriptMessage = {
  id: string;
  conversationId: string;
  position: number;
  role: "user" | "assistant";
  content: string;
  edited: boolean;
  createdAt: number;
  updatedAt: number;
  selectedRegenerationId: string | null;
  regenerations: Array<{
    id: string;
    messageId: string;
    content: string;
    createdAt: number;
  }>;
};

function toConversationSummary(record: NonNullable<Awaited<ReturnType<typeof getConversationSummaryById>>>) {
  return {
    id: record.id,
    characterId: record.character_id,
    characterName: record.character_name,
    characterAvatarUrl: record.character_avatar_url,
    updatedAt: record.updated_at,
    startedAt: record.started_at,
    lastMessageAt: record.last_message_at,
    lastPreview: record.last_preview
  };
}

function toMessageDto(message: {
  id: string;
  conversationId: string;
  position: number;
  role: "user" | "assistant";
  content: string;
  edited: boolean;
  createdAt: number;
  updatedAt: number;
  selectedRegenerationId: string | null;
  regenerations?: Array<{
    id: string;
    messageId: string;
    content: string;
    createdAt: number;
  }>;
}) {
  return {
    id: message.id,
    conversationId: message.conversationId,
    position: message.position,
    role: message.role,
    content: message.content,
    edited: message.edited,
    createdAt: message.createdAt,
    updatedAt: message.updatedAt,
    selectedRegenerationId: message.selectedRegenerationId,
    regenerations: (message.regenerations ?? []).map((regeneration) => ({
      id: regeneration.id,
      messageId: regeneration.messageId,
      content: regeneration.content,
      createdAt: regeneration.createdAt
    }))
  };
}

function toStreamError(error: unknown): { code: string; message: string } {
  if (error instanceof AppError) {
    return {
      code: error.code,
      message: error.message
    };
  }

  return {
    code: "INTERNAL_ERROR",
    message: "Something went wrong."
  };
}

async function loadTranscript(context: RequestContext, conversationId: string): Promise<TranscriptMessage[]> {
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
    conversationId: message.conversation_id,
    position: message.position,
    role: message.role,
    content: message.content,
    edited: Boolean(message.edited),
    createdAt: message.created_at,
    updatedAt: message.updated_at,
    selectedRegenerationId: message.selected_regeneration_id,
    regenerations: (grouped.get(message.id) ?? []).map((regeneration) => ({
      id: regeneration.id,
      messageId: regeneration.message_id,
      content: regeneration.content,
      createdAt: regeneration.created_at
    }))
  }));
}

function visibleContent(message: TranscriptMessage): string {
  return (
    message.regenerations.find((regeneration) => regeneration.id === message.selectedRegenerationId)?.content ??
    message.content
  );
}

async function requireOwnedConversation(context: RequestContext, conversationId: string) {
  await ensureConversationStreamingSchema(context.env);
  const conversation = await getConversationById(context.env, conversationId);
  if (!conversation) {
    throw new AppError(404, "CONVERSATION_NOT_FOUND", "Conversation not found.");
  }
  if (conversation.owner_user_id !== context.user!.userId) {
    forbidden("Conversations are private to their owner.");
  }
  return conversation;
}

function assertConversationUnlocked(conversation: Awaited<ReturnType<typeof getConversationById>>) {
  if (
    conversation?.active_run_id &&
    conversation.active_run_expires_at != null &&
    conversation.active_run_expires_at > Date.now()
  ) {
    throw new AppError(409, "STREAM_IN_PROGRESS", "Wait for the current reply to finish before changing the transcript.");
  }
}

async function acquireConversationRun(context: RequestContext, conversationId: string): Promise<string> {
  const runId = createId("run");
  const now = Date.now();
  const claimed = await claimConversationRun(context.env, conversationId, runId, now, now + RUN_LOCK_WINDOW_MS);
  if (!claimed) {
    throw new AppError(409, "STREAM_IN_PROGRESS", "Wait for the current reply to finish before changing the transcript.");
  }
  return runId;
}

async function buildAssistantContext(
  context: RequestContext,
  conversationId: string,
  options: {
    untilPosition?: number;
    appendedUserContent?: string;
  } = {}
) {
  const conversation = await requireOwnedConversation(context, conversationId);
  const character = await getCharacterById(context.env, context.user!.userId, conversation.character_id);
  if (!character) {
    throw new AppError(404, "CHARACTER_NOT_FOUND", "Character not found.");
  }
  const transcript = await loadTranscript(context, conversationId);
  const visibleTranscript = transcript
    .filter((message) => options.untilPosition == null || message.position < options.untilPosition)
    .map((message) => ({
      role: message.role,
      content: visibleContent(message)
    }));

  if (options.appendedUserContent) {
    visibleTranscript.push({
      role: "user",
      content: options.appendedUserContent
    });
  }

  return {
    conversation,
    character,
    transcript,
    messages: [
      { role: "system" as const, content: character.system_prompt },
      ...visibleTranscript
    ]
  };
}

function sseEvent(payload: object): Uint8Array {
  return new TextEncoder().encode(`data: ${JSON.stringify(payload)}\n\n`);
}

function safeEnqueue(controller: ReadableStreamDefaultController<Uint8Array>, payload: object): void {
  try {
    controller.enqueue(sseEvent(payload));
  } catch {
    // Ignore enqueue failures after the client has disconnected.
  }
}

function safeClose(controller: ReadableStreamDefaultController<Uint8Array>): void {
  try {
    controller.close();
  } catch {
    // Ignore double-close errors.
  }
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
    if (emittedChunk || signal.aborted) {
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
  const conversation = await requireOwnedConversation(context, message.conversation_id);
  assertConversationUnlocked(conversation);

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
  await updateConversationActivity(context.env, message.conversation_id, now);
}

export async function rewindConversation(context: RequestContext, messageId: string) {
  const message = await getMessageById(context.env, messageId);
  if (!message) {
    throw new AppError(404, "MESSAGE_NOT_FOUND", "Message not found.");
  }
  const conversation = await requireOwnedConversation(context, message.conversation_id);
  assertConversationUnlocked(conversation);

  const transcript = await loadTranscript(context, message.conversation_id);
  let remaining: TranscriptMessage[];
  try {
    remaining = rewindTranscript(transcript, messageId) as TranscriptMessage[];
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
  const conversation = await requireOwnedConversation(context, message.conversation_id);
  assertConversationUnlocked(conversation);

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
  await updateConversationActivity(context.env, message.conversation_id, Date.now());
}

export async function sendMessageAndStream(
  context: RequestContext,
  conversationId: string,
  userMessageId: string,
  content: string
): Promise<Response> {
  await requireOwnedConversation(context, conversationId);
  const runId = await acquireConversationRun(context, conversationId);

  try {
    const duplicate = await getMessageById(context.env, userMessageId);
    if (duplicate) {
      throw new AppError(409, "DUPLICATE_MESSAGE_ID", "This message has already been sent.");
    }

    const { conversation, character, transcript, messages } = await buildAssistantContext(context, conversationId, {
      appendedUserContent: content
    });
    const assistantMessageId = createId("message");
    const userNow = Date.now();
    const userPosition = (transcript.at(-1)?.position ?? -1) + 1;
    const userMessage = {
      id: userMessageId,
      conversationId,
      position: userPosition,
      role: "user" as const,
      content,
      edited: false,
      createdAt: userNow,
      updatedAt: userNow,
      selectedRegenerationId: null
    };

    await insertMessage(context.env, {
      id: userMessage.id,
      conversation_id: userMessage.conversationId,
      position: userMessage.position,
      role: userMessage.role,
      content: userMessage.content,
      edited: 0,
      created_at: userMessage.createdAt,
      updated_at: userMessage.updatedAt,
      selected_regeneration_id: null
    });

    const abortController = new AbortController();
    const stream = new ReadableStream<Uint8Array>({
      async start(controller) {
        safeEnqueue(controller, {
          type: "accepted_send",
          runId,
          conversationVersion: conversation.version,
          userMessage: toMessageDto(userMessage),
          assistantMessageId
        });

        try {
          const finalText = await streamAssistantReply(
            context,
            messages,
            (chunk) => {
              safeEnqueue(controller, {
                type: "delta",
                runId,
                textDelta: chunk
              });
            },
            abortController.signal
          );

          const assistantNow = Date.now();
          const assistantMessage = {
            id: assistantMessageId,
            conversationId,
            position: userPosition + 1,
            role: "assistant" as const,
            content: finalText,
            edited: false,
            createdAt: assistantNow,
            updatedAt: assistantNow,
            selectedRegenerationId: null
          };

          await insertMessage(context.env, {
            id: assistantMessage.id,
            conversation_id: assistantMessage.conversationId,
            position: assistantMessage.position,
            role: assistantMessage.role,
            content: assistantMessage.content,
            edited: 0,
            created_at: assistantMessage.createdAt,
            updated_at: assistantMessage.updatedAt,
            selected_regeneration_id: null
          });
          await updateConversationActivity(context.env, conversationId, assistantNow);
          await incrementCharacterActivity(context.env, character.id, assistantNow);

          const summary = await getConversationSummaryById(context.env, context.user!.userId, conversationId);
          const updatedConversation = await getConversationById(context.env, conversationId);
          if (!summary || !updatedConversation) {
            throw new AppError(500, "CONVERSATION_SYNC_FAILED", "Conversation state could not be finalized.");
          }

          safeEnqueue(controller, {
            type: "completed_send",
            runId,
            conversationVersion: updatedConversation.version,
            assistantMessage: toMessageDto(assistantMessage),
            conversationSummary: toConversationSummary(summary)
          });
        } catch (error) {
          if (!abortController.signal.aborted) {
            safeEnqueue(controller, {
              type: "failed",
              runId,
              ...toStreamError(error)
            });
          }
        } finally {
          await releaseConversationRun(context.env, conversationId, runId);
          safeClose(controller);
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
  } catch (error) {
    await releaseConversationRun(context.env, conversationId, runId);
    throw error;
  }
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
  const runId = await acquireConversationRun(context, message.conversation_id);

  try {
    const transcript = await loadTranscript(context, message.conversation_id);
    let latest: TranscriptMessage;
    try {
      latest = requireLatestAssistant(transcript, messageId) as TranscriptMessage;
    } catch (error) {
      throw new AppError(400, "CHAT_RULE_ERROR", (error as Error).message);
    }

    const { conversation, messages } = await buildAssistantContext(context, message.conversation_id, {
      untilPosition: latest.position
    });
    const regenerationId = createId("regen");
    const abortController = new AbortController();

    const stream = new ReadableStream<Uint8Array>({
      async start(controller) {
        safeEnqueue(controller, {
          type: "accepted_regenerate",
          runId,
          conversationVersion: conversation.version,
          messageId: latest.id,
          assistantMessageId: latest.id
        });

        try {
          const finalText = await streamAssistantReply(
            context,
            messages,
            (chunk) => {
              safeEnqueue(controller, {
                type: "delta",
                runId,
                textDelta: chunk
              });
            },
            abortController.signal
          );

          const regenerationNow = Date.now();
          const regeneration = {
            id: regenerationId,
            messageId: latest.id,
            content: finalText,
            createdAt: regenerationNow
          };

          await insertRegeneration(context.env, {
            id: regeneration.id,
            message_id: regeneration.messageId,
            content: regeneration.content,
            created_at: regeneration.createdAt
          });
          await updateMessageSelection(context.env, {
            messageId: latest.id,
            selectedRegenerationId: regeneration.id,
            updatedAt: regenerationNow
          });
          await updateConversationActivity(context.env, message.conversation_id, regenerationNow);

          const summary = await getConversationSummaryById(context.env, context.user!.userId, message.conversation_id);
          const updatedConversation = await getConversationById(context.env, message.conversation_id);
          if (!summary || !updatedConversation) {
            throw new AppError(500, "CONVERSATION_SYNC_FAILED", "Conversation state could not be finalized.");
          }

          safeEnqueue(controller, {
            type: "completed_regenerate",
            runId,
            conversationVersion: updatedConversation.version,
            messageId: latest.id,
            regeneration: {
              id: regeneration.id,
              messageId: regeneration.messageId,
              content: regeneration.content,
              createdAt: regeneration.createdAt
            },
            selectedRegenerationId: regeneration.id,
            conversationSummary: toConversationSummary(summary)
          });
        } catch (error) {
          if (!abortController.signal.aborted) {
            safeEnqueue(controller, {
              type: "failed",
              runId,
              ...toStreamError(error)
            });
          }
        } finally {
          await releaseConversationRun(context.env, message.conversation_id, runId);
          safeClose(controller);
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
  } catch (error) {
    await releaseConversationRun(context.env, message.conversation_id, runId);
    throw error;
  }
}
