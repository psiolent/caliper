# 0007 — Bad tool input is refused, not absorbed

**Decision.** Every value the model sends across the tool boundary is read strictly.
A shape that cannot be read, or a value that cannot be true, comes back as an error
naming the field and what arrived, so the agent retries and fixes it in the same turn.
Nothing is silently dropped, coerced, or replaced with a default.

**Why.** This was learned the hard way, repeatedly. In one turn the model sent a
`servings` map as a JSON *string*; the app ignored it, saved a food with no servings,
the log by "large egg" then failed, and the model re-saved and re-logged — two wasted
round trips and a confusing trail. Separately, the model invented a meal time of
`16:00` at 14:55, and a `delete: "yes"` flag would have been read as `false`, quietly
turning a delete into an edit. Each of those looked like it worked.

The same failure showed up once more, quietly: the model passed a *date* as a meal's
`at` (`"2026-10-18"`), and the parser read it as midnight, so a meal eaten at 15:38 was
stamped 00:00. A date is a missing time of day, not a time, so it is now refused like
anything else that cannot be read.

It shows up most often with a list: `servings` as a stringified map, and later
`ingredients` as a stringified array — the JSON of a list, sent as text. Both are
refused, and both errors end "-- not a string", because that is the mistake the model
keeps making. Refusing costs one round trip and is self-correcting; accepting it would
quietly save a recipe with no ingredients.

**Consequence.** Retries cost a round trip, but they are self-correcting and visible;
a silent default costs the same round trip through a failed downstream step, and hides
the cause. Only complain where the value is actually needed — a redundant `per_100g`
beside an `fdc_id` passes without comment, because erroring there would break a save
that works.

**Where it lives.** The shape readers and `parseWhen` are top-level in
`agent/Tools.kt` so they can be unit-tested (`ToolArgsTest`); `isFuture` guards against
times that cannot have happened.
