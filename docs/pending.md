# Pending

Open issues the user has raised. None are started. Read this before proposing work,
and ask the user which to take — they sequence this themselves.

Ordered roughly as the user listed them, not by priority.

## 1. History: group by day, and summarise each session

Group sessions under "Today", "Yesterday", "Saturday", "Last week". Each entry should
carry a short summary of what the session contained.

Open question raised by the user: where does the summary come from? Resummarising at
the end of every turn is one option, but it costs a model call per turn and would
write on turns that did nothing. Something cheaper may exist — worth thinking about
before building.

`ui/HistoryScreen.kt`, `data` (sessions and messages).

## 2. Settings: the model list needs containing

"Load current model list" dumps roughly 400 rows into the settings page. It needs its
own scrollable, searchable view rather than being inlined.

`ui/SettingsScreen.kt`.

## 3. A turn and leaving the screen (tested; one design question left)

Tested on the phone, so this part is answered:

- **Switching tabs** while a turn runs: the turn continues and the reply arrives. The
  chat view model belongs to the Activity, not the tab.
- **Home / backgrounding**: the turn continues too.
- **Back out of the app** (or swipe it away): the Activity is destroyed, the turn is
  cancelled, and only the user's message remains.

Fixed along the way: a cancelled turn used to write "I couldn't reach the model: Job
was cancelled" into the transcript — a lie, since nothing was wrong with the model. A
cancellation is now left to propagate instead of being reported as a failure.

Open design question: **should a turn outlive the app?** It cannot today without an
application-scoped worker, and surviving doze needs a foreground service. Note that it
would only be *useful* together with resuming the last session, because the app starts
a fresh session on every launch — a reply that landed after you left would sit in a
session you are no longer looking at. Worth deciding as one question, not two.

Related: a turn that ran while the screen was off failed once with "Unable to resolve
host openrouter.ai" — the phone suspends the network in low-power states. Another
argument for a foreground service if long turns matter.

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
