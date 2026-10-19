# 0011 — The day's allowance is the burn, moved by a goal

**Decision.** The Day screen leads with **calories left**, computed as
`(burn + goal) − eaten`. The goal is a signed daily adjustment set in Settings: `0` holds,
a **deficit is negative** (eat under what the day burns), a **surplus is positive**. The
arithmetic is `domain/CalorieBalance.kt`, and its sign convention is pinned by tests.

**Why.** Calories eaten is a record; calories left is the number you act on. And a
deficit or surplus is how a weight goal is actually stated — "500 under" — so it belongs
as one number rather than as a target the user computes for themselves.

One signed integer rather than two fields (`goal`, `direction`) keeps the model small: the
allowance is the burn moved by the goal, and the sign is the only thing that needs
explaining.

**Consequence.** Without a burn there is no allowance, so the block falls back to calories
eaten alone — no bar, no remainder. The bar is the allowance in three segments: what has
been eaten, the slice a deficit holds back, and what is left; a surplus widens the bar past
the burn so the three still add up. See `docs/decisions/0009` for what "burn" means — it is
the day's total, not a running tally.

Do not turn this into a target-weight or macro-goal system without asking. The one goal
here is a calorie adjustment.
