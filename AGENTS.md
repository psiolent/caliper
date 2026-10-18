# Caliper

Personal Android calorie and nutrition tracker. Chat-first input (text and photos),
on-device storage, no server. Built for a single user — there is one user, and their
intent outranks anything written here.

## Build and run

- **JDK**: requires 17+. This machine has a complete JDK 17 at
  `/usr/lib/jvm/java-17-openjdk-amd64`; the default `java` on `PATH` is a JRE with no
  compiler, so `app/build.gradle.kts` pins a Java 17 toolchain rather than relying on
  the ambient one.
- **Android SDK**: `/home/aja/Android/Sdk` (see `local.properties`).
  `compileSdk`/`targetSdk` 37, `minSdk` 26. The current AndroidX libraries require
  compiling against API 37.
- `./gradlew :app:assembleDebug` — build the debug APK.
- `./gradlew :app:testDebugUnitTest` — run unit tests.
- `./gradlew :app:installDebug` — install to a connected device.
- **Stack**: Kotlin + Jetpack Compose, single `:app` module. AGP 9 compiles Kotlin
  itself (the `kotlin-android` plugin is neither needed nor allowed); the Compose and
  serialization compiler plugins are still applied.

### Testing on the real device

The app is developed against a physical phone over adb, and this is how changes have
actually been verified — do the same rather than reasoning from the code.

- `adb shell am start -n psiolent.caliper/.MainActivity`, then drive the UI with
  `adb shell input tap X Y` and `adb shell uiautomator dump /sdcard/ui.xml` to find
  element bounds. Screenshot with `adb exec-out screencap -p > shot.png`.
- **Read the agent log.** Every tool call and its result is appended to
  `files/agent-log.txt` in the app's private storage, readable with
  `adb shell run-as psiolent.caliper cat files/agent-log.txt`. The system log rotates
  within minutes and loses whole turns; this file does not. It is the fastest way to
  see what the model actually did.
- To wipe stored data (with the user's agreement), delete the database files:
  `adb shell run-as psiolent.caliper rm -f databases/caliper.db*`. API keys live in
  `shared_prefs/caliper.settings.xml` and survive.
- `adb shell input text` needs `%s` for spaces.

### Configuration the user owns

The OpenRouter API key and the USDA FoodData Central key are entered in the app's
Settings and stored in `shared_prefs/caliper.settings.xml` on the device — not in this
repository. The model slots are set there too. Nothing about the app needs a key to
build or test the UI; only live turns do.

## Invariants

Load-bearing. Do not quietly break them.

1. **The database is memory.** All durable state lives in the local database. The
   agent's recall is the database plus the profile — never chat history.
2. **Sessions are ephemeral.** A session is a bounded, append-only conversation used
   only while the user is talking. It is stored as a transcript so the user can read
   it back, but the agent never reads old sessions.
3. **The app does the arithmetic.** The model decides *what* and *how much*; the app
   resolves units and multiplies nutrients. The model never converts units or
   computes nutrient totals.
4. **The agent writes; the user reviews.** The agent creates and edits everything.
   The user gets read-only views plus delete. There is no hand-built editor.
5. **The agent owns meals.** A meal is a row the agent creates and names. The app has
   no rule about what groups into one — that lives in the prompt, because only the
   agent can act on "no, that was two meals".
6. **Bad tool input is refused, not absorbed.** A wrong shape, an unknown nutrient
   key, or an impossible value (a meal in the future) comes back to the agent as an
   error it can correct. Silently dropping or defaulting it is the bug this project
   keeps re-learning.
7. **On-device only.** No server. The only network calls are to OpenRouter (models,
   web search) and USDA FoodData Central.
8. **Frugal by construction.** Local caching, small per-turn context, cheap models,
   downscaled images.

## Layout

Single module, packages by concern under `app/src/main/java/psiolent/caliper/`:

- `domain/` — pure logic: nutrients, units, arithmetic. No Android dependencies,
  unit-tested.
- `data/` — persistence: entities, DAOs, database and migrations, converters, the
  repository (the single entry point to state), settings, backup.
- `agent/` — OpenRouter client, tool schema, tool executor, system prompt, the runner
  that ties a turn together.
- `integrations/` — USDA FoodData Central, Health Connect, image handling, the agent
  log, and a minimal HTTP helper over `HttpURLConnection`.
- `ui/` — Compose screens (Chat, Day, Library, History, Settings) and one view model.

## Conventions

- Nutrients are a closed enum (`domain/Nutrients.kt`); a panel is a `Nutrients` value
  holding only known values. An absent nutrient means unknown, not zero.
- Nutrient values are tagged **measured** (a label, or USDA) or **estimated** (the
  model's guess). Soluble/insoluble fiber and mono/poly fat are often estimates from
  every source.
- Foods store nutrients per 100 g plus named servings with gram weights. Logging
  resolves a quantity to grams and scales.
- **A meal has a time; its items do not.** Items belong to a meal and list in the
  order they were logged.
- Replies are plain text. The chat does not render markdown, and the prompt asks for
  none — don't add a renderer without asking.

## Docs

- `docs/architecture.md` — how the pieces fit.
- `docs/data-model.md` — entities and columns.
- `docs/agent.md` — tools, prompt assembly, model slots, cost rules, the log.
- `docs/decisions/` — short records of *why*, so choices are not silently undone.
- **`docs/pending.md` — open issues the user has raised, agreed and unstarted. Read
  this before proposing work.**
