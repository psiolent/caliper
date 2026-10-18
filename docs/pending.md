# Pending

Open issues the user has raised. None are started. Read this before proposing work,
and ask the user which to take — they sequence this themselves.

Ordered roughly as the user listed them, not by priority.

## 1. Day view: the lower half has no grouping

The primary (calories) and secondary (protein/fat/carbs cards) sections work. Below
them, the fat breakdown, carb breakdown and the sodium/cholesterol line read as one
undifferentiated block: same weight, no visual cue about what belongs together or how
the pieces relate. The user's words: "chaotic and disordered… we need some visual cues
telling us what information belongs together and what the relationship is between
pieces of information."

Meals are better than they were — a rule separates a meal's heading from its items —
but there is still little distinguishing one meal from the next as a whole.

`ui/DayScreen.kt`. A design conversation is expected here, not a patch.

## 2. Tapping an item should show its full nutrition

Tapping an item in the day view should show all the detail held for it (the whole
snapshot panel, the food's source and servings, whether values are estimated). Today
the row shows only the label, amount and calories.

## 3. Library: no visual distinction, no search, and untested recipes/batches

Rows are undifferentiated and the list will grow without bound. Wants search, and
probably tabs for Foods / Recipes / Batches. Separately: **recipes and batches have
never been exercised at all** — no recipe has been saved and no batch made, so that
whole path is unverified.

`ui/LibraryScreen.kt`.

## 4. History: group by day, and summarise each session

Group sessions under "Today", "Yesterday", "Saturday", "Last week". Each entry should
carry a short summary of what the session contained.

Open question raised by the user: where does the summary come from? Resummarising at
the end of every turn is one option, but it costs a model call per turn and would
write on turns that did nothing. Something cheaper may exist — worth thinking about
before building.

`ui/HistoryScreen.kt`, `data` (sessions and messages).

## 5. Settings: the model list needs containing

"Load current model list" dumps roughly 400 rows into the settings page. It needs its
own scrollable, searchable view rather than being inlined.

`ui/SettingsScreen.kt`.

## 6. What happens if you navigate away mid-turn?

Unverified. The user asks whether a turn keeps running if they switch to the Day view
while the agent is working, or whether it is interrupted. The turn runs in the chat
view model's `viewModelScope`, so leaving the Chat screen should not cancel it — but
this has not been tested and should be.

`ui/ChatViewModel.kt`.

## 7. The chat does not scroll to a new message

When a reply lands below the fold, the list does not follow it.

`ui/ChatScreen.kt`.

## 8. Sent photos are not shown in the chat

The user's message shows only its text, so there is no sign a photo was sent. Note
that photos are deliberately never persisted — they exist only for the turn that reads
them — so showing them *after the fact* would need a decision about storing them (and
what that means for backup size). Showing them while the turn is live is the easy part.

`ui/ChatScreen.kt`, `integrations/Images.kt`.

## 9. Raise the agent's step limit

`AgentRunner.MAX_STEPS` is 20, which a single photo of a sushi roll exhausted (see
`docs/agent.md` on the agent log). The model is cheap — all development and testing so
far cost about 11 cents — so raise it to something like 100, or even 200. A bound is
still wanted, just not one this tight.

## Known friction, deliberately left alone

- **Serving names match exactly.** The model logged "1 banana" as unit `banana` against
  a USDA serving named `1 medium`, missed, and retried in grams. The result was right
  at the cost of a round trip. Loosening the match is the kind of widening the project
  has decided against; revisit only if it becomes annoying.

## Decided, do not reopen without asking

- **Markdown rendering** — decided against; the chat is plain text
  (`docs/decisions/0008`).
- **Meal labels as an enum** (`BREAKFAST`/`LUNCH`/…) — removed. Meals are agent-named
  strings now (`docs/decisions/0006`).
- **A notes store for the agent** — decided against; the database plus the profile is
  the agent's memory (`docs/decisions/0002`).
- **Our own voice input** — removed in favour of the keyboard's dictation, which is
  the OS recogniser and needs no permission from us.
