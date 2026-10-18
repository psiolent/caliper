# The agent

The model decides *what* and *how much*. The app resolves units and computes every
nutrient. That split is the whole design of this layer.

## Prompt assembly

Each turn sends, in order:

1. A system message with the static instructions (`Prompt.SYSTEM`).
2. A second system message with the briefing: today's date, the profile, today's
   totals, and batches on hand (`AgentRunner.context`).
3. The current session's earlier messages.
4. The new user turn, with an image part when a photo is attached.

Nothing older than the current session is included.

## Tools

Reads:
- `find_food(query)` — saved foods first, then USDA FoodData Central. Returns
  candidates; USDA candidates carry an `fdc_id`.
- `recent_batches()` — newest first, for resolving "those overnight oats".
- `get_log(date)` — a day's entries and totals.

Writes:
- `start_meal(label, at?)` — begin a meal; everything logged for it points at the id
  this returns. The agent decides how items group; the app only stores it.
- `save_food(...)` — create or update a food. Give `per_100g`, or an `fdc_id` and
  the app fetches the panel itself (the model never transcribes numbers).
- `log(meal_id, target_id, amount, unit, at?)` — add an item to a meal.
- `save_recipe(...)`, `update_recipe(...)`, `make_batch(...)` — the recipe/batch
  lifecycle.
- `revise_entry(entry_id, ...)` — fix or delete a logged entry.
- `update_profile(text)` — replace the profile document.

Plus OpenRouter's own `openrouter:web_search` server tool, added to every request.

Rules the tools enforce: a food is saved only when it is logged; lookup-only
candidates are dropped; the tool layer merges partial edits before calling the
repository, so the repository's contracts stay explicit.

## Model slots

Two slots, both editable in Settings: a chat/tool model and an optional vision
override used when a turn has a photo. The default is a cheap multimodal model.
Settings can pull the live model list from OpenRouter, so models change without an
app update.

## Cost rules

- The application database is the cache: a food's nutrition is looked up once.
- Context per turn is a briefing plus one session, never a growing history.
- Photos are downscaled to a JPEG capped at 1024 px before sending.
- The web search tool is model-controlled, not run on every request.
- Per-turn cost reported by the API is accumulated and shown in Settings.

## Errors

Tool failures are returned to the model as text (`"Error: ..."`), so it can adapt
rather than derail. If the loop hits `MAX_STEPS`, the turn ends with a short
message rather than looping.
