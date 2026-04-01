import type { Env } from "../../env";
import { all, first, run } from "../client";

export interface UserRecord {
  id: string;
  google_subject: string;
  email: string;
  created_at: number;
  last_login_at: number;
}

export interface ProfileRecord {
  user_id: string;
  display_name: string;
  avatar_url: string | null;
  created_at: number;
  updated_at: number;
  email?: string;
}

export async function getUserByGoogleSubject(env: Env, googleSubject: string): Promise<UserRecord | null> {
  return first<UserRecord>(
    env.DB.prepare("SELECT * FROM users WHERE google_subject = ? LIMIT 1").bind(googleSubject)
  );
}

export async function getProfileByUserId(env: Env, userId: string): Promise<ProfileRecord | null> {
  return first<ProfileRecord>(
    env.DB.prepare(
      `
      SELECT profiles.*, users.email AS email
      FROM profiles
      INNER JOIN users ON users.id = profiles.user_id
      WHERE profiles.user_id = ?
      LIMIT 1
      `
    ).bind(userId)
  );
}

export async function upsertUserAndProfile(env: Env, input: {
  userId: string;
  googleSubject: string;
  email: string;
  displayName: string;
  avatarUrl: string | null;
  now: number;
}): Promise<void> {
  await env.DB.batch([
    env.DB.prepare(
      `
      INSERT INTO users (id, google_subject, email, created_at, last_login_at)
      VALUES (?, ?, ?, ?, ?)
      ON CONFLICT(id) DO UPDATE SET
        google_subject = excluded.google_subject,
        email = excluded.email,
        last_login_at = excluded.last_login_at
      `
    ).bind(input.userId, input.googleSubject, input.email, input.now, input.now),
    env.DB.prepare(
      `
      INSERT INTO profiles (user_id, display_name, avatar_url, created_at, updated_at)
      VALUES (?, ?, ?, ?, ?)
      ON CONFLICT(user_id) DO UPDATE SET
        display_name = excluded.display_name,
        avatar_url = excluded.avatar_url,
        updated_at = excluded.updated_at
      `
    ).bind(input.userId, input.displayName, input.avatarUrl, input.now, input.now)
  ]);
}

export async function updateProfile(env: Env, userId: string, displayName: string, now: number): Promise<void> {
  await run(
    env.DB.prepare(
      `
      UPDATE profiles
      SET display_name = ?, updated_at = ?
      WHERE user_id = ?
      `
    ).bind(displayName, now, userId)
  );
}

