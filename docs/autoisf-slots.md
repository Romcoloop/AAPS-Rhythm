# AutoISF slots: own settings for meals, weekend and night

This fork of AndroidAPS adds **slots** to the AutoISF algorithm. A slot is a time window with its own
copy of the AutoISF and SMB delivery values. Inside the window the loop uses the values of the slot,
outside all windows it uses the global values, exactly as before.

Branches: `autoisf-custom` (base), `autoisf-slots` (phase 1), `autoisf-slots-2` (weekend and night),
`autoisf-analysis` (slots plus the log analysis card). Build: GitHub Actions workflow "AutoISF APK".

> **Before you install a new APK:** Maintenance → Export settings. Nothing in this feature changes a
> value on its own. Every value is set by you, in the settings or with the "Apply" button of the
> analysis card after a confirmation.

## 1. How a slot is chosen

There are seven slots:

| Slot | Used on | Window set in |
|---|---|---|
| Breakfast, Lunch, Dinner | Monday to Friday | AutoISF settings → Breakfast / Lunch / Dinner time |
| Breakfast, Lunch, Dinner (weekend) | Saturday, Sunday, holiday mode | Meal slot settings → … time (weekend) |
| Night | every day | Meal slot settings → Night time |

Rules:

- The slot is chosen **by the clock only**, once at the start of every loop run. Entering carbs does
  not change the slot.
- A window is written as `HH:MM-HH:MM`, for example `11:00-14:00`. The night may go over midnight,
  for example `23:00-05:30`. The end is not included: at `08:00` the breakfast window `05:30-08:00`
  is already over.
- An empty window means the slot is not used.
- Where a meal window and the night window overlap, **the meal wins**. Where two meal windows
  overlap, the first one in the order breakfast → lunch → dinner wins.
- Saturday and Sunday use the weekend slots. On a weekday the weekend slots are used while
  **Holiday or vacation** is on.
- Only the numbers are per slot: 9 AutoISF values, the IOB threshold and 5 SMB delivery values
  (15 in all). The on/off switches of AutoISF (SMB, UAM, autosens, …) stay global.

Switches and what "off" means:

| Switch | Off |
|---|---|
| Use meal slot settings | Behaviour exactly as without slots: global values everywhere. |
| Separate weekend slots | The weekday slots and weekday meal times are used every day. |
| Holiday or vacation | Weekend slots only on Saturday and Sunday. |

The meal times also drive the older "Meal factors outside meal times" scale. On a weekend day (or in
holiday mode, with separate weekend slots on) that scale uses the weekend meal times.

## 2. Where the settings are

All of it is in the settings of the **OpenAPS AutoISF** plugin.

**AutoISF settings** (unchanged): the global values, the weekday Breakfast / Lunch / Dinner time and
"Meal factors outside meal times".

**Meal slot settings**:

- Use meal slot settings
- Night time
- Separate weekend slots
- Breakfast / Lunch / Dinner time (weekend) – shown while separate weekend slots are on
- Holiday or vacation, and "Holiday or vacation ends on" (`YYYY-MM-DD`). With a date, the switch turns
  itself off the day after that date. Without a date you switch it off by hand.
- **Copy global values to all slots** – writes the global values into **all seven** slots. Values
  already in the slots are lost. Good once, at the very start.
- **Copy weekday slots to weekend** – writes the weekday breakfast / lunch / dinner values into the
  weekend slots. The weekday slots are not changed. Windows are never copied.

**Breakfast slot, Lunch slot, Dinner slot, Night slot, … slot (weekend)**: the 15 values of each slot.
A section is hidden while its switch is off.

## 3. First setup, in a safe order

A new slot starts with factory values (autoISF_max 1.0, all weights 0.0). Such a slot would run
almost without AutoISF. So fill a slot **before** you give it a window.

1. Use meal slot settings → on.
2. If the weekday slots are still equal to the global values: **Copy global values to all slots**.
   If you have already tuned the weekday slots: **do not** press it. Use **Copy weekday slots to
   weekend** instead, and fill the Night slot by hand with the 15 global values.
3. Set the weekday meal times (AutoISF settings) if they are not set yet.
4. Separate weekend slots → on, set the weekend meal times, for example `07:00-10:00`, `12:00-15:00`,
   `18:00-21:00`.
5. Night time, for example `23:00-05:30` – only once the Night slot has values.
6. Leave the values as they are for a few days. The point is to get log lines with slot names first,
   so the analysis can compare the slots.

## 4. Checking what the loop did

Every run writes one line into the AutoISF debug output (OpenAPS AutoISF tab, and the log files):

```
AutoISF slot: Dinner-weekend (18:00-21:00, weekend)
AutoISF slot: Night (23:00-05:30, weekday)
AutoISF slot: none, global values (holiday)
```

The first word after `slot:` is the slot (`Breakfast`, `Lunch`, `Dinner`, `Breakfast-weekend`, …,
`Night`, or `none`). In the brackets: the window, and the kind of day the run was on (`weekday`,
`weekend` or `holiday`). `holiday` means "Holiday or vacation" was on.

## 5. The log analysis card

The card **AutoISF log analysis** on the plugin screen reads the log files on the phone and shows what
AutoISF did.

- **Period**: 1 / 3 / 7 days. A weekend slot offers 14 / 28 days instead, because it sees only two
  meals a week.
- **Slot**: All, or one of the seven slots.
  - *All* = the whole day, the global values, as before. "Apply" writes to the global value.
  - *One slot* = only the meals of that slot: the runs of its window plus about 3 hours after it, one
    meal per day. A meal counts for a slot only when the log lines of that day name the slot, so a
    weekday breakfast window on a Saturday is not mixed into the weekday breakfast. "Apply" writes to
    the value of that slot.
- Per meal: BG at the start, peak above target and minutes to the peak, minutes above target +2 and
  +4 mmol/l, minutes below 3.9, BG at the end, SMB count and units, how often autoISF_max cut the
  factor, how often the SMB was as big as maxBolus allowed, the factor that drove ISF most.
- A meal with a temp target, or one that the log does not cover completely, is shown but not used.
- Suggestions are small steps and never written on their own. A slot that is switched off (meal
  slot settings off, or a weekend slot with separate weekend slots off) gets no suggestions, because
  the loop would not use the change.
- The Night slot is in the list, but it is still rated like a meal (peak, time to peak, …). Own night
  metrics are the next step.

## 6. What is planned

1. Night metrics in the analysis: lowest BG, the course 23:00 → 03:00 → morning, and a clear line
   between "the basal in the profile is wrong" and "an AutoISF value should change".
2. Suggestions per slot from a small rule table, with history and "Undo".
3. Automatic small changes with hard limits in code, after enough meals, with a global off switch.
4. Optional "Explain with Claude": the app sends only the summary and the suggestion, never raw logs,
   and the model never decides a dose.

## 7. For developers

- `plugins/aps/.../openAPSAutoISF/AutoIsfSlots.kt` – the slots, the selection, holiday mode and the
  copy buttons. `AutoIsfSlotsTest` covers the selection rules.
- `MealWindows.kt` – window text parsing and "over midnight".
- Keys: `autoISF_slot_<slot>_<value>` in `DoubleKey` / `IntKey`, windows `autoISF_meal_window_*` in
  `StringKey`, switches in `BooleanKey`. The weekend keys end in `_we_`, the night keys in `_night_`.
  The three weekday slots keep the keys from phase 1, so an update needs no migration.
- `logAnalysis/AutoIsfLogAnalysis.kt` – the analysis per slot, `AutoIsfMealAnalyzer.kt` – one meal.
- The unit tests of the APS module run in the "AutoISF APK" workflow with `runTests = true`.
