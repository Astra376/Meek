import type { Env } from "../../env";
import { createId } from "../../lib/ids";
import { streamChatText } from "../../providers/openrouter";
import { getConversationById, insertMessage, updateConversationActivity } from "../../db/queries/conversations";
import { getCharacterById } from "../../db/queries/characters";

// Run thresholds (in milliseconds)
const THRESHOLDS = [
  12 * 60 * 60 * 1000,
  6 * 60 * 60 * 1000,
  3 * 60 * 60 * 1000,
  1 * 60 * 60 * 1000,
  10 * 60 * 1000
];

export async function processOfflineMessages(env: Env) {
  const now = Date.now();
  
  // Logic: 
  // Select conversations where last_message_at < now - 10 minutes
  // We need a complex query to efficiently find candidates, or do it by evaluating recently active conversations.
  // We also have to guard against spam by ensuring the last message wasn't already an offline message for this threshold.
  // For simplicity since D1 queries are limited in this file, we fetch a list of open conversations that haven't been touched in a while.
  
  const results = await env.DB.prepare(`
    SELECT c.*, u.email, u.last_email_sent_at 
    FROM conversations c
    INNER JOIN users u ON u.id = c.owner_user_id
    WHERE c.last_message_at < ? 
      AND (
        SELECT role FROM messages m WHERE m.conversation_id = c.id ORDER BY position DESC LIMIT 1
      ) = 'user'
  `).bind(now - THRESHOLDS[4]).all<any>();

  // NOTE: In a production app, the WHERE clause would be broader, but the prompt says 
  // "if user was chatting with a character send a message after like 10 mins... characters that werent most recent chat send message 12 hours later."
  // For this implementation plan, we will process any conversation that needs an offline message:
  
  const queryAllIncomplete = await env.DB.prepare(`
    SELECT c.*, u.email, u.last_email_sent_at 
    FROM conversations c
    INNER JOIN users u ON u.id = c.owner_user_id
    WHERE ? - c.last_message_at >= ?
  `).bind(now, 10 * 60 * 1000).all<any>();

  const processable = queryAllIncomplete.results || [];

  for (const conv of processable) {
    // Determine the longest threshold crossed
    const idleTime = now - conv.last_message_at;
    const thresholdPassed = THRESHOLDS.find((t) => idleTime >= t);
    if (!thresholdPassed) continue;

    // Check if we ALREADY sent an offline-prompted message in this duration
    // A simple heuristic: if `unread_count > 0` and standard timeout rules apply,
    // we limit to 1 offline message until they read it.
    if (conv.unread_count > 0) continue;

    // Generate response text
    const character = await getCharacterById(env, conv.owner_user_id, conv.character_id);
    if (!character) continue;

    // Grab transcript
    const messagesRows = await env.DB.prepare(`
      SELECT role, content FROM messages WHERE conversation_id = ? ORDER BY position ASC LIMIT 50
    `).bind(conv.id).all<{role: string, content: string}>();

    const transcript = messagesRows.results ?? [];
    const messagesContext = [
      { role: "system" as const, content: character.system_prompt + "\n\n(OOC: The user has been silent for a while. Send a short, engaging message to re-initiate the conversation from your character's perspective.)" },
      ...transcript.map(m => ({ role: m.role as "user"|"assistant", content: m.content }))
    ];

    let fullText = "";
    try {
      const abortController = new AbortController();
      for await (const chunk of streamChatText(env, messagesContext, abortController.signal)) {
        fullText += chunk;
      }
    } catch (err) {
      console.error("OpenRouter fail", err);
      continue;
    }

    if (!fullText.trim()) continue;

    // Insert Message
    const assistantMessageId = createId("message");
    const newPosition = transcript.length + 1;
    await insertMessage(env, {
      id: assistantMessageId,
      conversation_id: conv.id,
      position: newPosition,
      role: "assistant",
      content: fullText.trim(),
      edited: 0,
      created_at: now,
      updated_at: now,
      selected_regeneration_id: null
    });

    // Update conversation
    await env.DB.prepare(`
      UPDATE conversations 
      SET last_message_at = ?, unread_count = unread_count + 1, has_unread_badge = 1
      WHERE id = ?
    `).bind(now, conv.id).run();

    // Check email logic for threshold 12h+
    if (thresholdPassed === THRESHOLDS[0]) {
      const lastEmailAt = conv.last_email_sent_at || 0;
      const daysSinceEmail = (now - lastEmailAt) / (1000 * 60 * 60 * 24);
      
      let shouldSendEmail = false;
      if (lastEmailAt === 0) shouldSendEmail = true;
      else if (daysSinceEmail >= 1 && daysSinceEmail < 3) shouldSendEmail = false; // "1 per day"
      else if (daysSinceEmail >= 3 && daysSinceEmail < 7) {
        // if > 3 days, send one email per 3 days
        if (daysSinceEmail >= 3) shouldSendEmail = true;
      } else if (daysSinceEmail >= 7) {
        // if > 1 week, once per week
        shouldSendEmail = true;
      }

      if (shouldSendEmail) {
        console.log(`[EMAIL DISPATCH] To: ${conv.email}. Subject: ${character.name} sent you a message! Body: "${fullText.trim().slice(0, 50)}..."`);
        await env.DB.prepare(`UPDATE users SET last_email_sent_at = ? WHERE id = ?`).bind(now, conv.owner_user_id).run();
      }
    }
  }
}
