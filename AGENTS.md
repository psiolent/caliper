# Caliper

Personal Android calorie and nutrition tracker. Chat-first input (text, voice, photo), on-device storage, no server. Built for a single user.

## Build and run

- **JDK**: requires 17+. This machine has a complete JDK 17 at `/usr/lib/jvm/java-17-openjdk-amd64`; the default `java` on `PATH` is a JRE with no compiler, so `app/build.gradle.kts` pins a Java 17 toolchain rather than relying on the ambient one.
- **Android SDK**: `/home/aja/Android/Sdk` (see `local.properties`). `compileSdk`/`targetSdk` 37, `minSdk` 26. The current AndroidX libraries require compiling against API 37.
- `./gradlew :app:assembleDebug` — build the debug APK.
- `./gradlew :app:testDebugUnitTest` — run unit tests.
- `./gradlew :app:installDebug` — install to a connected device/emulator.
- **Stack**: Kotlin + Jetpack Compose, single `:app` module. AGP 9 compiles Kotlin itself (the `kotlin-android` plugin is neither needed nor allowed); the Compose and serialization compiler plugins are still applied.

## Invariants

These are load-bearing. Do not quietly break them.

1. **The database is memory.** All durable state lives in the local database. The agent's recall is the database plus the profile — never chat history.
2. **Sessions are ephemeral.** A session is a bounded, append-only conversation used only while the user is talking. It is stored as a transcript so the user can read it back, but the agent never reads old sessions.
3. **The app does the arithmetic.** The model decides *what* and *how much*; the app resolves units and multiplies nutrients. The model never converts units or computes nutrient totals.
4. **The agent writes; the user reviews.** The agent creates and edits everything. The user gets read-only views plus delete/undo. There is no hand-built editor in V1.
5. **On-device only.** No server. The only network calls are to OpenRouter (models, web search) and USDA FoodData Central.
6. **Frugal by construction.** Local caching, small per-turn context, cheap models, downscaled images.

## Layout

Single module, packages by concern under `app/src/main/java/psiolent/caliper/`:

- `domain/` — pure logic: nutrients, units, arithmetic. No Android dependencies, unit-tested.
- `data/` — persistence: entities, DAOs, database, converters.
- `agent/` — OpenRouter client, prompt assembly, tool definitions and execution, model configuration.
- `integrations/` — USDA FoodData Central, Health Connect, speech, image handling.
- `ui/` — Compose screens and view models.

## Conventions

- Nutrients are a closed enum (`domain/Nutrients.kt`); a panel is a `Nutrients` value holding only known values. An absent nutrient means unknown, not zero.
- Nutrient values are tagged **measured** (read off a label, or from USDA) or **estimated** (the model's guess). Soluble/insoluble fiber and mono/poly fat are often estimates from every source.
- Foods store nutrients per 100 g plus named servings with gram weights. Logging resolves a quantity to grams and scales.

## Docs

- `docs/architecture.md` — how the pieces fit.
- `docs/data-model.md` — entities and columns.
- `docs/agent.md` — tools, prompt assembly, model slots, cost rules.
- `docs/decisions/` — short records of *why*, so choices are not silently undone.
