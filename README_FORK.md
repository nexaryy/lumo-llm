# Lumo (LLM-agnostic fork)

This is a fork of [Proton's Lumo](https://github.com/ProtonMail/android-lumo) that has been
**completely decoupled from Proton's API**. The app is no longer a WebView wrapper around
`lumo.proton.me` — it is now a **native Jetpack Compose chat client** that can talk to **any
LLM provider** via a configurable HTTP layer.

## What changed vs the upstream

| Upstream (Proton)        | This fork                                            |
| ------------------------ | ---------------------------------------------------- |
| WebView loading lumo.proton.me | Native Compose chat UI with the original cat |
| Proton account login     | No login screen — local-only                         |
| Chats auto-deleted after 7 days | Persistent Room database — never auto-deleted |
| Single assistant         | Multiple "Lumos" (like Custom GPTs)                  |
| No notifications         | Local NotificationManager fires when a response finishes |
| Vosk speech input        | Vosk speech input (kept)                             |

## Architecture

```
MainActivity
  └── NavHost (Jetpack Compose)
       ├── ChatScreen             ← main chat, with native message bubbles + cat avatar
       ├── LumoManagerScreen      ← list / create / delete Lumos
       ├── LumoEditorScreen       ← edit a Lumo (name, system prompt, model, temperature, …)
       ├── SettingsScreen         ← configure the LLM API (URL, key, headers, body template)
       └── SpeechSheet            ← Vosk voice input
```

Layers:

- **Data layer** (`data/`): Room database (`LumoDatabase`) with three tables —
  `lumos`, `conversations`, `messages`. Chats are **persistent** (no auto-deletion).
- **LLM client layer** (`llm/`): three interchangeable HTTP clients:
  - `OpenAiCompatibleClient` — speaks the OpenAI Chat Completions API (also works with
    OpenRouter, Together, Groq, Mistral, DeepSeek, xAI, Ollama with `/v1`, LM Studio, vLLM, …).
  - `AnthropicClient` — speaks Anthropic's Messages API (Claude family).
  - `CustomHttpClient` — a generic "bring your own endpoint" client. You give it a URL,
    method, headers and a body template with `{{prompt}}` / `{{system}}` / `{{history}}`
    placeholders, plus a JSONPath expression for where to find the answer in the response.
    Equivalent to `curl`-ing your model.
- **Notification layer** (`notification/` + `chat/LlmResponseService.kt`):
  - `LumoNotifier` — uses the local `NotificationManager` (NO Firebase / FCM, fully noGms-safe).
  - `LlmResponseService` — a foreground service that runs while the model is generating,
    so Android doesn't kill the stream when the user backgrounds the app.
- **Speech layer** (`speech/`): unchanged — Vosk / on-device / Google fallback chain.
- **DI** (`di/`): Hilt. `AppModule` provides Room, DataStore and the speech/permission pieces.

## How to configure the LLM

1. Open the app.
2. Tap the ⋮ menu → **Settings**.
3. Pick a **Provider type**:
   - **OpenAI-compatible** — for OpenAI, OpenRouter, Together, Groq, Mistral, DeepSeek, xAI,
     Ollama (with `/v1`), LM Studio, vLLM, etc. Enter your base URL (`https://api.openai.com/v1`,
     `https://openrouter.ai/api/v1`, `http://192.168.1.10:11434/v1`, …) and API key.
   - **Anthropic** — for Claude. Use `https://api.anthropic.com` as base URL. The client sets
     the `x-api-key` header and `anthropic-version` automatically.
   - **Custom HTTP** — for anything else. Fill in the URL, method, headers and body template.
     Use `{{prompt}}`, `{{system}}`, `{{history}}`, `{{model}}` placeholders in the body, and a
     JSONPath expression (e.g. `$.choices[0].message.content`) in the "Response path" field.
4. Tap **Save**.

## How to create a Lumo

1. Tap the ⋮ menu → **Manage Lumos**.
2. Tap the **+** button.
3. Fill in:
   - **Name** — what this Lumo is called (e.g. "Code reviewer", "Travel planner", "Therapist").
   - **System prompt** — instructions for the model.
   - **Model override** — leave blank to use the global default, or specify a specific model
     just for this Lumo (e.g. `gpt-4o-mini` for casual chat, `claude-3-5-sonnet-20241022`
     for serious work).
   - **Temperature override** — leave blank to use the global default.
   - **Avatar color** and **Avatar tag** — cosmetic.

## Notifications

While the model is generating, an **ongoing** notification ("Lumo is thinking…") is shown
with `IMPORTANCE_LOW` so it doesn't make a sound.

When the model finishes (or fails), a **dismissible** notification is posted on the
`lumo::done` channel (default importance, with vibration). Tapping it reopens the app at the
relevant conversation.

No Firebase / FCM is used — this works on GrapheneOS, LineageOS, or any degoogled ROM.

## Building

The project still has the upstream's three flavor dimensions (`env`, `services`, `debugging`).
The **noGms** flavor is the one that produces a degoogled APK:

```bash
# Debug APK (noGms)
./gradlew assembleNobleNoGmsDebug

# Release APK (noGms, suitable for F-Droid / sideloading)
./gradlew assembleProductionStandardNoGmsRelease
```

If you want a smaller project, you can delete the `gms` flavor and the `app/src/gms` source
set — only the Sentry initializer lives there now.

## Migration notes for existing Proton Lumo users

- Old Proton login / billing data is **not migrated** — there's nothing to log into.
- Old chats from the WebView are **not migrated** — there's no way to extract them from
  Proton's web app from outside.
- The theme preference (System / Light / Dark) is migrated automatically because it was
  stored in the same DataStore key.

## Privacy

- **No accounts, no telemetry, no Sentry in noGms builds.**
- API keys are stored locally in DataStore (encrypted at rest by Android's file-based
  encryption on supported devices).
- Chats are stored in a Room database on-device only. They are never uploaded anywhere by
  this app — they're only sent to the LLM endpoint you configured, as part of the request.
