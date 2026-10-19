# Data model

Room, one table per concept. Value objects that are always read and written with
their parent — a nutrient panel, a food's servings, a recipe's ingredient list —
are stored as JSON in a single column (`Converters`) rather than as join tables.
Nothing queries them independently, so joins would be surface without payoff.

## Tables

### `foods` — `FoodEntity`
Canonical nutrition for one food or product, stored **per 100 g**.
- `id`, `name`, `brand`
- `per100g: Nutrients` — the panel
- `estimated: Set<Nutrient>` — values the model guessed rather than looked up
- `servings: List<Serving>` — named portions and their gram weight ("cup = 80 g")
- `source: FoodSource` — `LABEL`, `USDA`, `WEB`, or `ESTIMATE`
- `sourceRef` — the USDA `fdcId` or source URL, for provenance
- `createdAt`

### `recipes` — `RecipeEntity`
A named formulation, and something you can log a portion of. Its nutrients are
*computed* from its ingredients, never entered.
- `id`, `name`
- `ingredients: List<Ingredient>` — each a `foodId`, `amount`, `unit`
- `yieldGrams` — total weight the recipe makes
- `servings: List<Serving>` — named portions of the finished dish. A portion named
  for a volume ("cup = 220 g") is a volume-to-weight ratio, and makes any volume unit
  loggable for that dish.
- `createdAt`

A recipe also answers to the unit `recipe` meaning the whole thing, which is how
"half a recipe" is said without anyone dividing the yield but the app (see
`RecipeEntity.portions`).

There is no batch table. A recipe *is* the thing you log: logging a portion computes
its panel from the ingredients at that moment, and the logged entry keeps its own
snapshot. See `docs/decisions/0010`.

### `meals` — `MealEntity`
A meal: a grouping the **agent** creates, holding the items eaten together.
- `id`
- `label` — the user's own word when they gave one ("lunch"), otherwise a short
  description the agent chose ("Toast and eggs")
- `at` — when the meal happened, shown once for the whole meal

The app has no rule about what makes a meal. The agent is instructed that everything
eaten in one session is one meal unless the user says otherwise, and it names each one.

### `log_entries` — `LogEntryEntity`
A single item eaten. It has no time of its own.
- `id`, `mealId` — the meal it belongs to
- `targetType: FOOD|RECIPE`, `targetId`
- `label` — the target's name at log time, so history renders without a join
- `amount`, `unit`, `grams` — the resolved weight
- `nutrients: Nutrients` — **snapshotted** at log time, so corrections to a food
  do not rewrite history

Items come back in the order they were logged. If that ever proves unstable, add an
explicit position then rather than a column that means nothing now.

### `profile` — `ProfileEntity`
A single row (`id = 1`) holding one short document: goals, targets, allergies,
dietary constraints, preferences. Always included in context.

### `sessions` and `messages`
The conversation transcript. `MessageRole` is `USER` or `ASSISTANT`. Stored for
the user to read back; the agent never reads these.

History is built from them directly: one query describes each session by its first user
message and its last reply (`SessionSummary`, in `SessionDao`). That is why there is no
summary column — the description is the transcript, read back, and cannot drift from it.

A user message may carry `thumbs: List<String>` — small copies (a ~2.5 KB JPEG data
URL each) of the photos sent with it, so the transcript can show what was sent. The
full image is still never kept.

## Notes

- Schema version is 5. Migrations live in `CaliperDatabase`; two of them rebuilt
  `log_entries` as the meal model settled, and the data was wiped alongside both by
  agreement with the user. The fifth dropped `batches` once a recipe became the thing
  you log, repointing anything logged against a batch at its recipe.
- An absent nutrient in a panel means *unknown*, not zero.
- `Nutrients` is a map over a closed `Nutrient` enum. Adding a nutrient is an enum
  value plus, if needed, a converter — no schema migration.
- Entities are `@Serializable` so the whole store round-trips as JSON for backup.
