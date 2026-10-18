# 0003 — The app does the arithmetic

**Decision.** The model chooses what and how much; the app resolves units and
computes nutrients.

**Why.** Unit conversion and nutrient arithmetic are deterministic. Letting the
model do them adds a whole class of quiet errors — a plausible number that is
subtly wrong — for no benefit. Keeping arithmetic in `domain` also makes it
unit-testable.

**Consequence.** `log` takes an amount and a unit and does the rest. `save_food`
can take a USDA `fdc_id` so the model never transcribes a numeric panel.
