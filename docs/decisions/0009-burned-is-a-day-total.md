# 0009 — The burned figure is the day's total, not a tally

**Decision.** The Day screen shows what today will come to — "will burn 1991 kcal today
· 308 active so far" — rather than what has been burned so far.

**Why.** Health Connect holds no `TotalCaloriesBurnedRecord` for the day at all; reading
the records returns none. The metric is derived by Health Connect itself: the active
calories so far plus a resting burn for the *whole* day. The arithmetic gives it away —
1991 − 308 = 1683, a day's resting burn — so the number already covers hours that have
not happened yet and barely moves as the day passes.

Shown as "burned 1991 kcal total" it read as a running tally that had stopped. The user
noticed the same value for hours and went looking for a bug; the bug was the label.

**Consequence.** Do not relabel this as calories burned so far, and do not put it beside
calories eaten without saying the two cover different spans. Two related facts are worth
keeping:

- The resting part is an estimate derived from the profile, not a measurement.
- The whole figure is only as fresh as its source. The phone's own active-calorie
  records stopped at 12:48 one afternoon and nothing was wrong; the figure sat still
  because the person was sitting still.

So the Day screen shows the burn's resting and active parts beneath the total, which is
what makes a still figure explicable at a glance. A reading-time note was tried first and
removed: the split says the same thing without a second number to read. The day re-reads
the figure every minute while it is open, so it moves as soon as the source does.

## Where the active figure comes from

**Decision.** The active figure is read from the `ActiveCaloriesBurnedRecord`s and taken
from **one app** — the one reporting the most for the day — not from Health Connect's
`ACTIVE_CALORIES_TOTAL` aggregate. `selectBurn` does it and is tested.

**Why.** The aggregate drops records that overlap. A ring wrote 167 kcal for the day and
569 kcal for a walk inside it, and its own app showed 736. Health Connect kept the
day-long record and discarded the workout, so the app showed 167 — right in the data,
wrong to the person looking at their wrist. The two records were meant to be added; the
app's own arithmetic says so.

One app's numbers, not a mixture, because two trackers covering the same hours would
double count. "The one reporting the most" is a heuristic: the app tracking you all day
reports more than one that caught part of it.

**Consequence.** The total is adjusted by the same amount as the active figure
(`total − aggregateActive + active`), because Health Connect derives its total from its own
active number. That keeps `total − active = resting` true, which the screen relies on. The
burn's split names the app — "736 active from Oura" — because a disagreement with another
tracker is the usual reason someone distrusts this number.

