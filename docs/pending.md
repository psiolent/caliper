# Pending

Open issues the user has raised. None are started. Read this before proposing work,
and ask the user which to take — they sequence this themselves.

Right now there is nothing to pick up: the one item below is decided, and the intent is to
use the app and let real use produce the next list.

Ordered roughly as the user listed them, not by priority.

## 1. A turn and leaving the screen (tested; decided to live with it)

Tested on the phone, so this part is answered:

- **Switching tabs** while a turn runs: the turn continues and the reply arrives. The
  chat view model belongs to the Activity, not the tab.
- **Home / backgrounding**: the turn continues too.
- **Back out of the app**: the Activity is destroyed, the turn is cancelled, and only the
  user's message remains. Swiping it from recents should behave the same way (same
  mechanism) but has not been tried.

Fixed along the way: a cancelled turn used to write "I couldn't reach the model: Job
was cancelled" into the transcript — a lie, since nothing was wrong with the model. A
cancellation is now left to propagate instead of being reported as a failure.

**Decided: live with it.** Making a turn outlive the app would need an application-scoped
worker and a foreground service to survive doze, and it is only useful together with
resuming the last session, since the app starts a fresh one on every launch. The user
would rather use the app as it is and find out whether it bites. Revisit only if it does.

The risk while backgrounded is not the turn being cancelled but the system: the app can be
killed for memory, and the phone suspends the network in low-power states — a turn that ran
with the screen off failed once with "Unable to resolve host openrouter.ai".

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
- **Chasing prompt-cache savings** — measured on the device: the GLM 5.3 routes credit a
  few hundred of the ~3,700 tokens each turn sends, sometimes none, and a perfect cache
  would save about a tenth of a cent per turn. The request is stable and `session_id`
  pins the provider (`docs/decisions/0013`, `docs/agent.md`); trimming the tool
  definitions is the only reliable saving and it costs the guidance that keeps tool calls
  well-formed. Decided to leave it, not to engineer around it.
