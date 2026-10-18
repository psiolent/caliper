# 0008 — The chat is plain text

**Decision.** Replies are shown as plain text. The chat does not render markdown, and
the system prompt asks the model for none.

**Why.** The model wrote `**761 kcal**`, and the asterisks showed literally. Rendering
markdown was the alternative and was on the list as a "nice to have"; the user's
verdict was "I think I like the plaintext chat. Let's drop markdown decision." A
renderer is real machinery — a parser, a styling pass, decisions about which subset to
support — to decorate short confirmations that read fine as they are.

**Consequence.** If a future change wants bold or lists, this is the record saying the
question was asked and answered. Don't add a renderer without asking.
