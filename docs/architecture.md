# Architecture

Caliper is a single-module Android app. Everything runs on the device; the only
network calls go to OpenRouter (models and web search) and to USDA FoodData
Central.

## Layers

- `domain` — pure logic, no Android dependencies. The nutrient vocabulary
  (`Nutrient`), a nutrient panel (`Nutrients`), named servings (`Serving`), and
  unit resolution (`resolveGrams`). Unit-tested.
- `data` — persistence. Room entities and DAOs, JSON type converters, the
  repository (the single entry point to state), settings, and the backup codec.
- `agent` — the OpenRouter client, the tool schema, the tool executor, the system
  prompt, and the runner that ties a turn together.
- `integrations` — USDA FoodData Central, Health Connect, image downscaling, and a
  minimal HTTP helper over `HttpURLConnection`.
- `ui` — Compose screens (Chat, Day, Library, Settings) and one view model.

## One turn

1. The user sends text and/or a photo from the Chat screen (voice input is the
   keyboard's own dictation, not ours).
2. `AgentRunner.send` stores the user message, then assembles a small context: the
   system prompt, a briefing (today, the profile, today's totals, batches on
   hand), the current session's earlier messages, and the new turn.
3. It calls the model with the tool schema. If the model asks for tools, the
   executor runs them against the repository and appends the results; the loop
   repeats, bounded by `AgentRunner.MAX_STEPS`, until the model replies with text.
4. The reply is stored and shown. Durable facts are already in the database.

## The memory model

The database is memory. A conversation is a bounded, append-only session, stored
as a transcript for the user to read but **never read back by the agent**. The
profile is a small document always present. There is no growing history window:
each turn sees only the briefing and the current session.

## Reading state

The Day and Library screens read straight from Room through `CaliperRepository`.
No model is involved in displaying or summing anything.

## Nutrition sourcing

Tiered, first hit wins: a package photo (the vision model reads the label),
`find_food` (saved foods, then USDA), the model's own web search, and finally the
model's knowledge as a flagged estimate. Every food records where its panel came
from, and each value is tagged measured or estimated.
