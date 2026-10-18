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
A named formulation. Its nutrients are *computed*, never entered.
- `id`, `name`
- `ingredients: List<Ingredient>` — each a `foodId`, `amount`, `unit`
- `yieldGrams` — total weight the recipe makes
- `servings: List<Serving>` — named portions of the finished dish
- `createdAt`

### `batches` — `BatchEntity`
One dated preparation of a recipe.
- `id`, `recipeId`, `name`
- `ingredients: List<Ingredient>` — **a snapshot** taken at creation
- `totalGrams`
- `per100g: Nutrients` — computed from the snapshot at creation
- `estimated: Set<Nutrient>`, `servings`, `createdAt`

Because the snapshot is fixed, editing a recipe later changes future batches only.
"How much is left" is never stored; it is derived from logged consumption.

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
- `targetType: FOOD|BATCH`, `targetId`
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

## Notes

- Schema version is 3. Migrations live in `CaliperDatabase`; two of them rebuilt
  `log_entries` as the meal model settled, and the data was wiped alongside both by
  agreement with the user.
- An absent nutrient in a panel means *unknown*, not zero.
- `Nutrients` is a map over a closed `Nutrient` enum. Adding a nutrient is an enum
  value plus, if needed, a converter — no schema migration.
- Entities are `@Serializable` so the whole store round-trips as JSON for backup.
