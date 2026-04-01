# AI Character Chat

Android-only AI character chat app with a local-first mock mode now and a Cloudflare Worker backend scaffold ready to wire up later.

## Repo layout

- `android-app/`: Kotlin Android app using Compose, Hilt, Room, Retrofit, Credential Manager, Coil, Coroutines, and Flow.
- `backend/`: Cloudflare Worker scaffold using D1, R2, OpenRouter, and fal integration points.
- `docs/`: setup instructions for Android, Worker, secrets, and device install steps.

## Current mode

The Android app is configured to run in mock mode by default so it remains installable and explorable before backend credentials exist.

When you are ready to connect the real backend:

1. Deploy the Worker in `backend/`
2. Set the Android Gradle properties described in [docs/setup.md](docs/setup.md)
3. Flip `AI_CHAT_USE_MOCK` to `false`

## Tooling note

This workspace did not include Java, Gradle, Node, or npm, so I could not execute builds locally in this environment. The project files are structured to build once those toolchains are installed.

## Setup

See [docs/setup.md](docs/setup.md) for:

- Worker secrets and `wrangler.toml` values
- Android Gradle properties
- Google sign-in setup
- D1 migration steps
- Phone run instructions
- Test commands
