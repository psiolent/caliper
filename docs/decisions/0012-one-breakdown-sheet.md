# 0012 — One breakdown sheet, and destructive actions live inside it

**Decision.** Any nutrient panel — the day's totals, a logged item, a food, a recipe — is
shown by the same bottom sheet (`ui/BreakdownSheet.kt`). In the Library, delete lives
inside that sheet rather than on the row, and asks first, naming whatever depends on the
thing being deleted.

**Why.** The panel had been formatted separately in the day view and the library, and they
drifted: one showed "saturated 19" without units, the other "per 100 g: …". A single
component means a panel is broken down in one place and reads the same wherever it is
looked at, and a new screen gets it for free.

Delete was a text button on every library row. It made the list hard to read, it was easy
to hit by accident, and it could not be undone. Behind a deliberate tap on the thing
itself, with a question first, is the right place for something irreversible.

The question names dependents because deleting a food is not local: a recipe that uses it
can no longer be worked out, and a logged item keeps its values but stops being
adjustable. The warning says which recipes, by name.

**Consequence.** A screen that shows nutrition opens this sheet instead of formatting
numbers itself. Anything irreversible asks before acting and says what breaks. Deleting is
still allowed — the alternative, refusing, would trap a user who wants to remove a bad food
and repair the recipe afterwards.
