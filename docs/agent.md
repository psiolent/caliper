# The agent

The model decides *what* and *how much*. The app resolves units and computes every
nutrient. That split is the whole design of this layer.

## Prompt assembly

Each turn sends, in order:

1. One system message: the static instructions (`Prompt.SYSTEM`) followed by the profile.
2. The current session's earlier messages, each replayed exactly as it was sent — a turn
   that carried photos is replayed with its photos.
3. The new user turn: the briefing (today's date, today's totals) followed by the user's
   own words, with one image part per attached photo.

Stable first, volatile last, so a provider's prompt cache can be reused: everything ahead
of the newest turn is unchanged since the last request, and the briefing, which changes
with every logged bite, rides at the tail where changing it costs only that turn
(`docs/decisions/0013`). `AgentRunner.requestMessages` builds this and is unit-tested.

The full-size photos of the open session are held in memory by `AgentRunner` and dropped
when the session ends; the database keeps only the thumbnails that History shows.

Nothing older than the current session is included. The loop is bounded by
`AgentRunner.MAX_STEPS` (100), generous because the model is cheap and a photo can
take many steps; the bound stops a runaway, it does not hurry the agent.
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
- `find_food(query)` — saved foods first, then USDA FoodData Central. Saved foods are
  ranked: an exact phrase, then every word in any order, then a near miss — a typo or a
  partial phrase — which comes back as "closest saved foods" rather than a match. A miss
  says how many foods are saved, so it cannot be mistaken for an empty library. USDA is
  searched only when nothing saved is even similar.
- `recipes()` — the recipes saved so far, by name, each with the portions it can be
  logged in. Recipes are not otherwise readable, so this is how one you defined earlier
  is found (and how a wrong recipe id is recovered from).
- `get_log(date)` — a day's meals, their items, and totals.

Writes:
- `start_meal(label, at?)` — begin a meal; everything logged for it points at the id
  this returns.
- `update_meal(meal_id, label?, at?)` — rename or re-time an existing meal.
- `delete_meal(meal_id)` — remove a meal and its items.
- `save_food(...)` — create or update a food. Give `per_100g`, or an `fdc_id` and the
  app fetches the panel itself (the model never transcribes numbers). Reuses an
  existing row with the same name and brand rather than adding a duplicate.
- `log(meal_id, target_id, amount, unit)` — add an item to a meal. The target is a food
  or a recipe. No time: the item belongs to the meal's. A unit that cannot be resolved
  comes back as an error naming the ones that would work. A recipe also answers to the
  unit `recipe` meaning the whole dish, so half of it is `0.5 recipe`.
- `save_recipe(...)`, `update_recipe(...)` — defining a recipe and changing it. A recipe
  is logged against directly; anything already logged keeps the panel it was logged with.
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
- A bare date is not a time: `"2026-10-18"` is refused, because reading it as midnight
  stamped a meal at 00:00 that was eaten in the afternoon.
- `parseWhen` and the shape readers are top-level and unit-tested (`ToolArgsTest`,
  `WhenParsingTest`).

## Model slots

Two slots, both editable in Settings: a chat/tool model and an optional vision
override used when a turn has a photo. The default is a cheap multimodal model.
Settings can pull the live model list from OpenRouter, so models change without an
app update.

## Cost rules

- The application database is the cache: a food's nutrition is looked up once.
- Context per turn is a briefing plus one session, never a growing history.
- The request is stable at the front so a provider's prompt cache can be reused
  (`docs/decisions/0013`), and every request carries the session id as OpenRouter's
  `session_id`, which both pins the conversation to one provider and keys the model
  host's cache. Measured on the device, the GLM 5.3 routes reuse only a few hundred of
  the ~3,700 tokens a turn sends, so the promise is largely unmet there. The log line
  `── usage:` reports the tokens, cached and written, that each turn actually cost.
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
rather than derail. Two guards stop a turn that cannot adapt:

- **Stuck**: if the same tool is refused for ten steps in a row, with nothing succeeding
  in between, the turn stops and says what kept failing. A step counts once however many
  calls it made, and any success resets the count (`RefusalStreak`).
- **Out of room**: if the loop reaches `MAX_STEPS`, the turn ends saying the work is
  saved and offering to continue.

Both are recorded in the agent log, since a turn that gave up is the thing worth reading
about later.
