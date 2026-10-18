# 0002 — No separate notes store

**Decision.** There is no freeform "agent notes" table. The durable stores are the
structured database and a single profile document.

**Why.** Enumerate what a note would hold — a brand's label, a preference, a goal,
a leftover, a quirk about a restaurant — and each lands in a food, the profile, or
a recipe. A notes store would be machinery built before a problem exists, and a
second place the agent could look for the truth. Add one later if knowledge shows
up that genuinely fits nowhere.

**Consequence.** The agent's recall is exactly two things: the database and the
profile.
