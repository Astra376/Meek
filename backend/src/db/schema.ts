export const schemaNotes = {
  users: "Stores Google-authenticated users.",
  profiles: "Stores editable profile fields.",
  characters: "Stores public and private characters.",
  characterLikes: "Stores likes on public characters.",
  conversations: "Stores user-owned conversation roots.",
  conversationMemories: "Stores private short-term and long-term roleplay memory per conversation.",
  messages: "Stores the linear transcript.",
  assistantRegenerations: "Stores alternate latest assistant replies."
} as const;
