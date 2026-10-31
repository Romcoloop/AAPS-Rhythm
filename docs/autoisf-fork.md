# AutoISF fork: blended BG, meal times, slots and the tuning card

This fork of AndroidAPS (github.com/Romcoloop/AndroidAPS) changes the **OpenAPS AutoISF** plugin in
four places. Everything else is upstream AndroidAPS.

| What | Where in the app | Off means |
|---|---|---|
| 1. Blended BG for bg_ISF | AutoISF settings → Blended BG ISF weight | weight 0.0 = as upstream |
| 2. Meal times and "meal factors outside meal times" | AutoISF settings | no meal time set = as upstream |
| 3. Slots: own values per meal, weekend and night | Meal slot settings + slot sections | switch off = as upstream |
| 4. AutoISF log analysis (the tuning card) | card on the AutoISF plugin screen | reads only; writes on "Apply" |
| 5. Steps and activity (branch `autoisf-steps`) | AutoISF settings → Steps and activity | all 4 switches off = as upstream |

Branches: `autoisf-custom` (1, 2, 4) → `autoisf-slots` (3, phase 1) → `autoisf-slots-2` (weekend,
night) → `autoisf-analysis` (everything, plus 4 per slot) → `autoisf-steps` (5, steps). **Install
builds from `autoisf-analysis`**, or from `autoisf-steps` if you want the step rules.
Build: GitHub Actions workflow "AutoISF APK" (phone and wear APK; `runTests = true` runs the APS unit
tests first).

> **Before you install a new APK:** Maintenance → Export settings.
> Nothing here changes a value on its own. Every value is set by you: in the settings, or with the
> "Apply" button of the analysis card after a confirmation dialog.
> This is insulin dosing. Change one value at a time, in small steps, and talk to your diabetes team.

## 1. Blended BG

`bg_ISF` normally looks at the current BG only, so it reacts late when BG rises fast. With a weight
above 0 the BG used for `bg_ISF` is moved up by a part of the rise the parabola expects in the next
20 minutes: `BG + weight × (predicted BG − BG)`. The idea comes from Boost.

Rules in code (`BlendedBg.kt`):

- only when the parabola fit is good (correlation ≥ 0.9, the same limit acce_ISF uses),
- only at or above target, and only when BG rises; falling BG always uses the real BG,
- the lift is capped at 36 mg/dl (2 mmol/l), the weight at 0.5,
- it changes `bg_ISF` only; `acce_ISF`, `pp_ISF` and `dura_ISF` are not touched,
- the final ISF is still limited by `autoISF_min` / `autoISF_max`.

The log shows it as `bgBlend` lines. A weight of 0.1–0.2 is a careful start.

## 2. Meal times and the factor outside meal times

Three windows in AutoISF settings: Breakfast / Lunch / Dinner time, written as `HH:MM-HH:MM`. Inside
a window the meal factors (`bgBlend`, `pp_ISF` and `acce_ISF` above target) work with full weight.
Outside all windows they are multiplied by **Meal factors outside meal times** (1.0 = no change,
0.5 = half, 0.0 = off). Braking when BG falls is never reduced. With no window set, nothing changes.

The same windows are the windows of the weekday meal slots (chapter 3). On Saturday, Sunday and in
holiday mode the weekend meal times are used instead, when separate weekend slots are on.

## 3. Slots

A slot is a time window with its own copy of the 15 AutoISF numbers: 9 AutoISF values, the IOB
threshold and 5 SMB delivery values. Inside the window the loop uses the values of the slot, outside
all windows the global values. The on/off switches of AutoISF (SMB, UAM, autosens, …) stay global.

### 3.1 The seven slots

| Slot | Used on | Window set in |
|---|---|---|
| Breakfast, Lunch, Dinner | Monday to Friday | AutoISF settings → Breakfast / Lunch / Dinner time |
| Breakfast, Lunch, Dinner (weekend) | Saturday, Sunday, holiday mode | Meal slot settings → … time (weekend) |
| Night | every day | Meal slot settings → Night time |

- The slot is chosen **by the clock only**, once at the start of every loop run. Entering carbs does
  not change the slot.
- The night may go over midnight, for example `23:00-05:30`. The end of a window is not included: at
  `08:00` the breakfast window `05:30-08:00` is already over.
- An empty window means the slot is not used.
- Where a meal window and the night window overlap, **the meal wins**. Where two meal windows
  overlap, the first one in the order breakfast → lunch → dinner wins.
- Saturday and Sunday use the weekend slots. On a weekday the weekend slots are used while
  **Holiday or vacation** is on.

| Switch | Off |
|---|---|
| Use meal slot settings | Behaviour exactly as without slots: global values everywhere. |
| Separate weekend slots | The weekday slots and weekday meal times are used every day. |
| Holiday or vacation | Weekend slots only on Saturday and Sunday. |

### 3.2 Where the settings are

All in the settings of the OpenAPS AutoISF plugin.

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

### 3.3 First setup, in a safe order

A new slot starts with factory values (`autoISF_max` 1.0, all weights 0.0). Such a slot would run
almost without AutoISF. So fill a slot **before** you give it a window.

1. Use meal slot settings → on.
2. If the weekday slots are still equal to the global values: **Copy global values to all slots**.
   If you have already tuned the weekday slots: **do not** press it. Use **Copy weekday slots to
   weekend** instead, and fill the Night slot by hand (chapter 5 has the values).
3. Set the weekday meal times (AutoISF settings) if they are not set yet.
4. Separate weekend slots → on, set the weekend meal times, for example `07:00-10:00`, `12:00-15:00`,
   `18:00-21:00`.
5. Night time, for example `23:00-05:30` – only once the Night slot has values.
6. Leave the values as they are for a few days. The point is to get log lines with slot names first,
   so the analysis can compare the slots.

### 3.4 Checking what the loop did

Every run writes one line into the AutoISF debug output (OpenAPS AutoISF tab, and the log files):

```
AutoISF slot: Dinner-weekend (18:00-21:00, weekend)
AutoISF slot: Night (23:00-05:30, weekday)
AutoISF slot: none, global values (holiday)
```

The first word after `slot:` is the slot (`Breakfast`, `Lunch`, `Dinner`, `Breakfast-weekend`, …,
`Night`, or `none`). In the brackets: the window, and the kind of day (`weekday`, `weekend` or
`holiday`; `holiday` means "Holiday or vacation" was on).

## 4. The tuning card: AutoISF log analysis

The card on the AutoISF plugin screen reads the log files on the phone (`AndroidAPS.log` and the
zipped older ones) and shows what AutoISF did. It never changes a value by itself.

### 4.1 Controls

- **Period**: 1 / 3 / 7 days. A weekend slot offers 14 / 28 days instead, because it sees only two
  meals a week.
- **Slot**: *All*, or one of the seven slots.
  - *All* = the whole day, the global values. "Apply" writes to the global value.
  - *One slot* = only the meals of that slot: the runs of its window plus 3 hours after it, one meal
    per day. A meal counts for a slot only when the log lines of that day name the slot, so a weekday
    breakfast window on a Saturday is not mixed into the weekday breakfast. "Apply" writes to the
    value of that slot. A slot that is switched off gets no suggestions.
- **Analyze** runs it. The result stays on screen until you change the slot.

### 4.2 What it shows

**Summary** (whole period, or the meals of the slot): hours covered, number of runs, time below
3.0 / below 3.9 / in range 3.9–10 / above 10 mmol/l, SMB count and units, how often `autoISF_max`
cut the factor and how often the SMB delivery ratio was at its maximum, which factor drove ISF most.

**Meals** (one slot only): per meal the BG at the start, the peak above target and the minutes to
the peak, minutes above target +2 and +4 mmol/l, minutes below 3.9, BG at the end, SMB count and
units, how often `autoISF_max` cut the factor, how often the SMB was as big as maxBolus allowed, the
factor that drove ISF most. A meal with a temp target, or one that the log does not cover completely,
is shown but not used for the statistics. The statistics line shows **n = meals used**.

**Events**: every SMB that was followed by a low (below 3.9) within 3 hours, or by a predicted low
that stopped SMBs (`minGuardBG`), with the factor that drove that SMB.

### 4.3 How a suggestion is made

Rules, no model. Nothing is suggested from less than 12 hours of runs with the same settings (6 hours
for one slot), and a change of a setting starts a new period.

*Too much insulin* – when there were at least 2 lows after SMBs, or more than 4 % of the time below
3.9, or enough predicted lows: look at the SMBs before the lows. If at least half of them …

| … were | suggestion |
|---|---|
| cut by `autoISF_max` | `autoISF_max` − 0.1 |
| driven by acce_ISF | `bgAccel_ISF_weight` × 0.8 |
| driven by bg_ISF | `higher_ISFrange_weight` × 0.8 |
| at the maximum SMB delivery ratio | `smb_delivery_ratio_max` − 0.1 |
| as big as maxBolus allowed | `smb_max_range_extension` − 1 |

*Too little insulin* – only when there was no low and no event at all, more than 25 % of the time
above 10 mmol/l, and at least 24 runs high with the prediction still above target:

| … | suggestion |
|---|---|
| `autoISF_max` cut the factor in ≥ 30 % of those runs | `autoISF_max` + 0.1 |
| else bg_ISF drove them in ≥ 30 % | `higher_ISFrange_weight` × 1.1 |

Every suggestion names the number of events behind it, the value before and after, and one reason.
**Apply** writes it after a confirmation, and refuses if the value changed meanwhile. Values are
clamped to the limits of the setting.

### 4.4 How to tune with it

1. Start with *All* over 7 days: it shows whether the global values are safe (lows) before anything
   per slot.
2. Then one slot at a time, over 7 days (weekend 14–28). Look at **n**: fewer than about 5 meals is
   not a pattern. Apply at most one suggestion per slot, then wait for the next meals.
3. Lows always win over highs: a slot with lows gets less insulin first, even when it also has highs.
4. The Night slot is in the list, but it is still rated like a meal. Own night metrics (lowest BG, the
   course 23:00 → 03:00 → morning, basal in the profile vs. an AutoISF value) are the next step.

## 5. Starting values

The table shows the global values of the author's own setup, read from his log of 2026-10-27 (14:09),
next to the AutoISF defaults. They are **one person's values**, not a recommendation for anyone else;
the point is that the slots start from the same numbers and only differ where the analysis says so.

| Setting | Author (global) | AutoISF default | Note |
|---|---|---|---|
| autoISF_max | 1.6 | 1.0 (off) | 1.6 is already strong: ISF can be made 60 % stronger. |
| autoISF_min | 0.6 | 1.0 (off) | ISF can be weakened to 60 % when BG falls. |
| bgAccel_ISF_weight | 0.3 | 0.0 | Reacts to a fast rise. On 2026-10-27 acce_ISF hit the 1.6 cap. |
| bgBrake_ISF_weight | 0.08 | 0.0 | Brake on a fast fall. |
| pp_ISF_weight | 0.09 | 0.0 | Post-prandial rise. |
| lower_ISFrange_weight | 0.3 | 0.0 | Below target: weaker ISF. |
| higher_ISFrange_weight | 0.6 | 0.0 | Above target: stronger ISF. |
| dura_ISF_weight | 0.03 | 0.0 | Long plateaus above target. |
| Blended BG ISF weight | set by hand | 0.0 | Fork only (chapter 1). 0.1–0.2 is a careful start. |
| iob_threshold_percent | 90 % | 100 % | Above this share of max IOB no SMB. |
| smb_delivery_ratio | 0.5 | 0.5 | |
| smb_delivery_ratio_min | 0.6 | 0.5 | |
| smb_delivery_ratio_max | 0.8 | 0.5 | 0.8 = up to 80 % of the insulin needed in one SMB. |
| smb_delivery_ratio_bg_range | 0.25 | 0.0 | In mmol/l when below 10. |
| smb_max_range_extension | 5.0 | 1.0 | With an even target this allows an SMB of 5 × 30 min of basal (about 1 U at 0.39 U/h). This is what made the 0.95 U SMB on 2026-10-27. |
| SMB on even target | on | off | Together with the line above. |

Also from that log, for context: max IOB 6 U, max basal 1.9 U/h (≈ 5 × basal 0.39), autosens max 1.6,
maxSMBBasalMinutes 30, UAM 30, target 6.2 mmol/l, ISF 2.27 mmol/l/U, CR 12 g/U.

Starting values for the slots, until the analysis says otherwise:

| Slot | Start with |
|---|---|
| Breakfast, Lunch, Dinner (weekday) | the values you have tuned; they stay as they are |
| … (weekend) | Copy weekday slots to weekend |
| Night | the global values, but milder: `autoISF_max` 1.4, `bgAccel_ISF_weight` 0.15, `smb_delivery_ratio` 0.4 / min 0.5 / max 0.7, `smb_max_range_extension` 3. You sleep, so the loop should have less room, not more. |

## 6. What is planned

1. Night metrics in the analysis: lowest BG, the course 23:00 → 03:00 → morning, and a clear line
   between "the basal in the profile is wrong" and "an AutoISF value should change".
2. Suggestions per slot from a small rule table (peak, time to peak, lows early / late), with history
   and "Undo".
3. Automatic small changes with hard limits in code, after enough meals (3 of the last 5), with a
   global off switch. Weekend slots only after 10 meals.
4. Optional "Explain with Claude": the app sends only the summary and the suggestion, never raw logs,
   and the model never decides a dose.
5. Later ideas: steps and heart rate from the watch as an activity signal (log first, act later),
   a manual stress / sick-day mode with its own 15 values.

## 7. For developers

- `plugins/aps/.../openAPSAutoISF/BlendedBg.kt` – chapter 1. `MealWindows.kt` – windows, "over
  midnight", the outside-meal scale.
- `AutoIsfSlots.kt` – the slots, the selection, holiday mode and the copy buttons. `AutoIsfSlotsTest`
  covers the selection rules.
- Keys: `autoISF_slot_<slot>_<value>` in `DoubleKey` / `IntKey`, windows `autoISF_meal_window_*` in
  `StringKey`, switches in `BooleanKey`. The weekend keys end in `_we_`, the night keys in `_night_`.
  The three weekday slots keep the keys from phase 1, so an update needs no migration.
- `logAnalysis/AutoIsfLogParser.kt` – one run per `Result: RT(...)` line, settings from the
  `OapsProfileAutoIsf(...)` line. `AutoIsfLogAnalyzer.kt` – statistics, events and the rule table of
  4.3. `AutoIsfMealAnalyzer.kt` – one meal. `AutoIsfLogAnalysis.kt` – the analysis per slot and
  "Apply".
- The unit tests of the APS module run in the "AutoISF APK" workflow with `runTests = true`. The
  preference schema snapshot (`app/src/testFull/resources/prefs-schema.txt`) is kept by hand and
  sorted.

## 8. Steps and activity (branch `autoisf-steps`)

Four switches in AutoISF settings → **Steps and activity**. All are off; off = as upstream. The rules
are ported from OpenAPS AIMI (MTR93600/OpenApsAIMI, AGPL-3.0) and **only reduce**: they never add an
SMB or basal. Without step data nothing fires.

| Switch | What it does |
|---|---|
| Count steps with the phone | Reads the phone's step sensor and stores the steps in the database the way a watch does (5/10/15/30/60/180 minute rows, once a minute). Needs the **Physical activity** permission; the app asks for it in the permissions sheet. This also makes the upstream automation trigger "Steps count" work without a watch. Steps from a connected watch are used too (the phone wins while it runs). |
| Sport guard | During a burst of steps (≥ 400 in 5 min **and** ≥ 800 in 10 min) or sustained activity (≥ 1200/30 min, ≥ 3000/60 min or ≥ 4500/180 min) **no SMB**, and the temp basal stays at or under the profile basal. Released as soon as there are 5 minutes with 0 steps, for example after a walk while a meal is rising. |
| Longer SMB interval after activity | After > 1500 steps in 3 h with BG < 120 mg/dl (6.7) at least 10 min between SMBs; while still moving (> 100/5 min and > 500/30 min, last SMB older than 20 min) at least 15 min. |
| Effort protection | From 25 steps/min the SMB gets smaller, at a brisk walk (40/min and more) down to ×0.45. After the walk the protection fades over 30 min (a short walk) up to 120 min (30+ min of walking). A short burst (5 minute window only) reduces the SMB now but does not start the long protection; that needs the 15 minute window. |

The loop log has a line `steps: 5m=… 10m=… 30m=… 60m=… 180m=…; …` and the reason carries
`sport guard: no SMB`, `effort x0.62: SMB 0.80 -> 0.40U` or `Waiting …`.

A safe start: first only **Count steps with the phone**, look at the step graph and the `steps:` log
line for a day or two. Then **Effort protection** (the mildest, gradual), then **Sport guard**. The
upstream automation with the trigger "Steps count" + the action "Start temp target" replaces AIMI's
"target 130 while active" rule, so that rule is not here.

For developers: `openAPSAutoISF/steps/` – `StepBuckets.kt` (minute buckets, window sums),
`PhoneStepCounter.kt` (interface; `AndroidPhoneStepCounter` in androidMain, iOS and desktop answer
"not available"), `AutoIsfSteps.kt` (store to the database, read, memory), `StepsActivityRules.kt`
and `EffortActivityBelief.kt` (pure rules, tests in `commonTest`). `determine_basal` takes
`steps_activity: StepsActivity`. Keys `autoISF_steps_*` in `BooleanKey`.
