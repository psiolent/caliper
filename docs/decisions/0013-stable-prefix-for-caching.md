# 0013 — The request is stable at the front, volatile at the tail

**Decision.** A turn's request is ordered so that everything stable comes first:

1. one system message holding the prompt and the profile,
2. the session's earlier messages, each replayed exactly as it was sent, photos included,
3. the newest user turn, prefixed with the briefing — today's date and today's totals.

Nothing that changes from turn to turn sits ahead of the transcript. The full-size photos
of a session are kept in memory for as long as that session is open and dropped when the
next one starts; the database keeps only thumbnails.

**Why.** Providers cache the longest matching prefix of a request and charge less for it,
so a request that repeats its own history byte for byte is cheaper every turn. The
briefing used to sit in a second system message, right after the prompt — position two —
and it changes as soon as anything is logged. Every turn invalidated the whole transcript
behind it, which is most of the input.

Replaying the transcript exactly is the other half. Turns carrying photos were replayed as
text, so the prefix broke at that message and, worse, a follow-up question could not see a
photo sent two turns earlier. The model would say it could not see a picture it had just
read, and the user had to send it again.

**Consequence.** Anything that changes per turn goes at the tail, after the transcript.
Anything that changes rarely — the prompt, the profile — can lead. A profile rewrite
mid-session still invalidates the cached prefix once; that is rare and accepted. Full
images exist only in memory, for the open session only, so a restart loses them — but a
restart also begins a new session, so there is no turn that expects them back. The cost is
bounded by the session: images replayed are the ones the user sent in it.
