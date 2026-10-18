# 0010 — A recipe is the thing you log; there are no batches

**Decision.** A batch is gone. A recipe is both the formulation and the thing you log
portions of: `log` takes a recipe id, and the item logged against it carries its own
nutrient snapshot. `make_batch` and `recent_batches` no longer exist.

**Why.** A batch existed to do three things, and each turned out to be worth less than
it looked.

- **Freeze the formula for one make**, so editing the recipe later would not change
  leftovers being eaten. But every logged entry snapshots its own panel, so the past is
  safe either way. The freeze only decided which formula a *future* log used — a narrow
  case, and arguably the wrong answer: you would rather the recipe you just fixed apply
  from now on.
- **Record that the thing was made**, with a date. Nothing ever read that except the
  "remaining" figure below.
- **Track how much is left.** This was a claim the app could not support: it counted
  what the user had logged, and the user shares their overnight oats with someone else,
  so the number was simply wrong. They said it did not matter.

Against that sat an entity, two tools, a section of the Library, a line in every turn's
briefing, and a two-step flow for what should be one sentence.

**Consequence.** "I had 200 g of my overnight oats recipe" is now one turn. Two things
came with it:

- A recipe answers to the unit `recipe`, so "half a recipe" and "a quarter of it"
  resolve without anyone dividing the yield. That division is exactly the arithmetic the
  model is not allowed to do, so it had to be the app's.
- A portion named for a volume ("cup = 220 g") is a volume-to-weight ratio for the dish,
  because the unit resolver already converts within the volume dimension. A dish that
  declares one can be logged in any volume unit; a dish that does not gets a refusal.

Dropping the table is migration 4→5: items logged against a batch are repointed at the
recipe that batch was made from, and the batch table is dropped. Their own snapshots are
what they render from, so nothing else had to move.
