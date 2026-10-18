# 0001 — Session-scoped context, not a growing history

**Decision.** A conversation is a bounded, append-only session (roughly a task, a
handful of turns). Durable facts go to the database. When the session ends, the
thread leaves working memory.

**Why.** Provider prompt caching makes a long shared prefix cheap only while the
cache is warm; caches expire within minutes to an hour. Personal use is
intermittent — breakfast, then dinner hours later — so a long thread would be
re-sent at full price several times a day. Bounding context by session sidesteps
that, and matches the instinct that one ever-growing context is the wrong shape.

**Consequence.** References like "those oats" are resolved by querying the
database (`recipes`), not by the model remembering an earlier message.
