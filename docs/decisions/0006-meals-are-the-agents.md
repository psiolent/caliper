# 0006 — Meals are the agent's, not derived by the app

**Decision.** A meal is a row the agent creates (`start_meal`) with a name and a time;
items point at it. The app never groups items into meals itself.

**Why.** The first attempt derived meals by clustering entries logged close together in
time. The user rejected that: "I want the agent to add stuff I've eaten *by meal*, with
the items I ate for that meal." A time heuristic cannot act on "no, that was two meals",
and it invents a grouping nobody asked for. The rule — everything eaten in one session
is one meal unless told otherwise — is a judgement about what the user meant, so it
lives in the prompt, where it can be corrected in conversation.

**Consequence.** The app has no opinion about grouping, which is why it also has no
`Unlabelled` bucket and no inferred meal names. The cost is one more tool and the
agent having to be disciplined about reusing an open meal; the benefit is that
correcting a grouping is a sentence, not a redesign.

**Also decided here.** Items carry no time of their own; the meal does. An item with
its own timestamp let the model read "about 10 minutes ago" as `10:47` and stamp an
entry four hours off its meal.
