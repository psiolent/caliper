# 0004 — The agent writes; the user reviews

**Decision.** The agent creates and edits everything. The user gets read-only
views (Day, Library) plus delete. There is no hand-built editor in V1.

**Why.** The premise of the app is "say things and it sorts them out", so the
agent as the writer fits, and precise edits are what chat is good at. A real
editor — ingredient pickers, unit dropdowns, validation — is far more machinery
for a benefit that may never be needed.

**Consequence.** Because the agent is the only writer, the read-only views and
delete matter: they are how a wrong turn stays visible and reversible.
