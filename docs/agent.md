# The agent

The model decides *what* and *how much*. The app resolves units and computes every
nutrient. That split is the whole design of this layer.

## Prompt assembly

Each turn sends, in order:

1. A system message with the static instructions (`Prompt.SYSTEM`).
2. A second system message with the briefing: today's date, the profile, today's
   totals, and batches on hand (`AgentRunner.context`).
3. The current session's earlier messages.
4. The new user turn, with one image part per attached photo.

Nothing older than the current session is included. The loop is bounded by
`AgentRunner.MAX_STEPS` (currently 20 — see `docs/pending.md`; it is too low).

## What the prompt owns

Some rules deliberately live in the prompt rather than in the app, because they are
judgements about what the user meant and only the agent can act on a correction:

- **Meals.** Everything the user says they ate in one session is one meal unless they
  say otherwise; use the current time unless they say when; name it with the user's
  own word ("lunch") or a short description ("Toast and eggs"), never a bare
  time-of-day guess. If a meal is already open and empty, use it rather than starting
  a second one.
- **Provenance.** Say where a panel came from; mark estimated values.
- **When to ask.** If a food's identity or quantity is genuinely unclear, ask one
  short question rather than guessing.

The app stores what the agent decides. It has no opinion about grouping.

## Tools

Reads:
- `find_food(query)` — saved foods first, then USDA FoodData Central. Returns
  candidates; USDA candidates carry an `fdc_id`.
- `recent_batches()` — newest first, for resolving "those oats I made".
- `get_log(date)` — a day's meals, their items, and totals.

Writes:
- `start_meal(label, at?)` — begin a meal; everything logged for it points at the id
  this returns.
- `update_meal(meal_id, label?, at?)` — rename or re-time an existing meal.
- `delete_meal(meal_id)` — remove a meal and its items.
- `save_food(...)` — create or update a food. Give `per_100g`, or an `fdc_id` and the
  app fetches the panel itself (the model never transcribes numbers). Reuses an
  existing row with the same name and brand rather than adding a duplicate.
- `log(meal_id, target_id, amount, unit)` — add an item to a meal. No time: the item
  belongs to the meal's.
- `save_recipe(...)`, `update_recipe(...)`, `make_batch(...)` — the recipe/batch
  lifecycle.
- `revise_entry(entry_id, amount?, unit?, delete?)` — fix or delete a logged item.
- `update_profile(text)` — replace the profile document.

Plus OpenRouter's own `openrouter:web_search` server tool, added to every request.

## The tool boundary

Every value the model sends is read strictly, and anything that cannot be read is
returned as an error naming the field and what arrived, so the agent corrects it in
the same turn. This is a deliberate posture, not an accident of implementation — see
`docs/decisions/0007`. In particular:

- A field of the wrong type is refused, not read as absent.
- `servings` and `ingredients` accept only their documented shapes (a list of
  objects, or the equivalent map); a stringified one is refused.
- An unknown nutrient key is reported in the save result rather than dropped.
- A time in the future is refused: nobody has eaten yet.
- `parseWhen` and the shape readers are top-level and unit-tested (`ToolArgsTest`).

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
- Per-turn cost reported by the API is accumulated and shown in Settings. Total spend
  after all development and testing so far: about 11 cents.

## The agent log

`integrations/AgentLog` appends every turn and every tool call with its result to
`files/agent-log.txt` in the app's private storage, rolling at 256 KB. It exists
because the system log rotates within minutes and had already destroyed the twenty
steps of a bad turn before it could be read. Read it with:

```
adb shell run-as psiolent.caliper cat files/agent-log.txt
```

## Errors

Tool failures are returned to the model as text (`"Error: ..."`), so it can adapt
rather than derail. If the loop hits `MAX_STEPS`, the turn ends saying the work is
saved and offering to continue.
