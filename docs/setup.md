# Setup Guide

This repo is split into:

- `android-app/`: the Android client
- `backend/`: the Cloudflare Worker

The Android app runs in local mock mode by default. When you are ready for real auth, persistence, portraits, and model responses, wire the Worker first, then point the Android client at it.

## 1. Install the local toolchain

Install these on your machine first:

- Android Studio with Android SDK Platform 35 and JDK 17
- Node.js 20+
- npm 10+
- Wrangler 4 (`npm install -g wrangler` is fine if you prefer a global install)

This workspace did not have Java, Gradle, Node, or npm available, so I could not generate the full Gradle wrapper jar or run the builds here.

## 2. Android project settings

The Android build flags live in:

- `/home/connor/projects/AIChat/AIChatApp/android-app/gradle.properties`

Edit these keys there:

```properties
AI_CHAT_USE_MOCK=false
AI_CHAT_API_BASE_URL=https://YOUR_WORKER_SUBDOMAIN.workers.dev/
AI_CHAT_GOOGLE_WEB_CLIENT_ID=YOUR_GOOGLE_WEB_CLIENT_ID.apps.googleusercontent.com
```

What each one does:

- `AI_CHAT_USE_MOCK`
  Set to `true` for the seeded local demo app.
  Set to `false` to use the real Worker.
- `AI_CHAT_API_BASE_URL`
  Must end with `/`.
  This is the deployed Cloudflare Worker base URL.
- `AI_CHAT_GOOGLE_WEB_CLIENT_ID`
  This must be the Google OAuth **Web application** client ID, not the Android one.

## 3. Google sign-in setup

Because the app uses Credential Manager + Google ID tokens, create these Google Cloud OAuth clients:

1. Create an **Android** OAuth client.
2. Package name must be:
   `com.example.aichat`
3. Add your debug SHA-1 and release SHA-1 fingerprints to that Android OAuth client.
4. Create a **Web application** OAuth client.
5. Put the **Web application client ID** in both places:
   - `/home/connor/projects/AIChat/AIChatApp/android-app/gradle.properties` as `AI_CHAT_GOOGLE_WEB_CLIENT_ID`
   - `/home/connor/projects/AIChat/AIChatApp/backend/wrangler.toml` under `[vars]` as `GOOGLE_WEB_CLIENT_ID`

You do not need `google-services.json` for this implementation because the app is not using Firebase Auth SDKs.

## 4. Cloudflare Worker setup

### 4.1 Install backend dependencies

```bash
cd /home/connor/projects/AIChat/AIChatApp/backend
npm install
```

### 4.2 Create the Cloudflare resources

Create a D1 database:

```bash
wrangler d1 create character-chat
```

Create an R2 bucket:

```bash
wrangler r2 bucket create character-chat-assets
```

Then update:

- `/home/connor/projects/AIChat/AIChatApp/backend/wrangler.toml`

Fill in these fields:

- `database_id`
  Use the value returned by `wrangler d1 create`
- `database_name`
  Keep `character-chat` unless you intentionally renamed it
- `bucket_name`
  Keep `character-chat-assets` unless you intentionally renamed it
- `GOOGLE_WEB_CLIENT_ID`
  Put your Web OAuth client ID here
- `OPENROUTER_MODEL`
  Put the OpenRouter model slug you want to use
- `FAL_MODEL`
  Put the fal model slug you want to use
- `R2_PUBLIC_BASE_URL`
  Put the public base URL for your bucket or asset domain

### 4.3 Apply the database schema

Run the backend migrations from:

- `/home/connor/projects/AIChat/AIChatApp/backend/src/db/migrations/0001_initial.sql`
- `/home/connor/projects/AIChat/AIChatApp/backend/src/db/migrations/0002_conversation_streaming.sql`

Commands:

```bash
wrangler d1 execute character-chat --remote --file=src/db/migrations/0001_initial.sql
wrangler d1 execute character-chat --remote --file=src/db/migrations/0002_conversation_streaming.sql
```

### 4.4 Create Worker secrets

These secrets must be created with `wrangler secret put` from inside `/home/connor/projects/AIChat/AIChatApp/backend`.

Create the session signing secret:

```bash
wrangler secret put SESSION_HMAC_SECRET
```

Use a long random value, for example 32+ bytes from a password manager or:

```bash
openssl rand -base64 48
```

Create the OpenRouter API key:

```bash
wrangler secret put OPENROUTER_API_KEY
```

Create the fal API key:

```bash
wrangler secret put FAL_API_KEY
```

Do **not** put these secrets in `wrangler.toml`.

### 4.5 Deploy the Worker

```bash
npm run deploy
```

After deployment, copy the Worker URL into:

- `/home/connor/projects/AIChat/AIChatApp/android-app/gradle.properties`

Set:

```properties
AI_CHAT_API_BASE_URL=https://YOUR_WORKER_SUBDOMAIN.workers.dev/
```

## 5. Run the Android app immediately

### 5.1 Mock mode

If you want to run the app right away without the Worker:

1. Keep `AI_CHAT_USE_MOCK=true` in `/home/connor/projects/AIChat/AIChatApp/android-app/gradle.properties`
2. Open `/home/connor/projects/AIChat/AIChatApp/android-app` in Android Studio
3. Let Android Studio install missing SDK components
4. If the Gradle wrapper jar is missing, open Terminal in `android-app/` and run:

```bash
gradle wrapper
```

5. Sync the project
6. Connect your Android phone with USB debugging enabled, or start an emulator
7. Select the `app` run configuration
8. Press Run

The app will sign into the seeded local demo account and the full UI shell will work with Room-backed local data and mock streaming.

### 5.2 Real backend mode

After the Worker is deployed:

1. Set `AI_CHAT_USE_MOCK=false`
2. Set `AI_CHAT_API_BASE_URL` to your Worker URL
3. Set `AI_CHAT_GOOGLE_WEB_CLIENT_ID` to your Web client ID
4. Sync the project again
5. Run the app on your phone

## 6. Test commands

Run Android unit tests from `/home/connor/projects/AIChat/AIChatApp/android-app`:

```bash
./gradlew testDebugUnitTest
```

Run backend tests from `/home/connor/projects/AIChat/AIChatApp/backend`:

```bash
npm test
```

## 7. What is ready now vs later

Ready now:

- full Android app shell
- sign-in gate
- home/search
- character studio
- chats list
- chat screen
- destructive edit
- destructive rewind
- latest-only regenerate
- variant selection for latest assistant
- light/dark themes
- Room-backed local state
- backend scaffold

Needs real credentials/resources to go live:

- Google token verification
- Worker deployment
- D1 persistence in Cloudflare
- R2 asset serving
- OpenRouter model responses
- fal portrait generation
