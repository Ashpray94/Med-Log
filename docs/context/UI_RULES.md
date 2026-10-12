# UI rules

Every rule here was asked for by the owner, most of them after a bug. `docs/DESIGN.md` has the original principles
(one job per screen, patterns, type sizes, older hands and eyes). Where the two differ, this file wins.

## Layout
- **No horizontal scrolling**, anywhere: chips wrap (`FlowRowOf`), charts fit the card, no carousels or pagers.
- Cards: **white on a light grey page, with a soft lift** (`Modifier.lift`, `Card`). Nothing may blend into the page.
- **No thick borders and no coloured side stripes** on cards. Status goes in a thin banner along the bottom of a card
  (`StatusBanner`) or a tag, not a frame.
- Card rows the same shape every time: picture top-aligned with the first line of the name; name, dose and one line
  of times in one column; buttons at the bottom. **Details always on the left**, the answer on the right.
- With large text or Hindi/Tamil, side-by-side buttons **stack** rather than split a word.
- Lists of many (150 medicines): one card on Home plus "See all", a full list on its own page.
- **Sheets** (`AppSheet`) open at 75% of the height and grow to 90%, never under the status bar.
- The bottom bar only on top-level pages; deeper pages get Back and SOS at the top.

## Colour
- One accent per mode: **teal for my health, blue for helping** (`MY_BRAND`, `HELPER_BRAND` in `ui/Theme.kt`).
  Use it sparingly: the one main action, the chosen item, the current tab.
- **Green only means done** (given, taken, all done, answered "I'm coming"). Never a button colour. Actions are
  `Tone.PRIMARY` (accent) or quiet tones. `Tone.OK` must not be used for buttons.
- Red: SOS, urgent, missed. Amber: due now, to watch. Grey: later, not given, food instead.
- Words on the accent are white: text takes its colour from what it sits on (no fixed ink colour in the text style).
- Feature tints (icon tiles) are fixed per feature; Food is cocoa brown, not green. In helper mode, teal tints become slate.
- `ColourRulesTest` fails the build on a green button or a mode colour typed outside `Theme.kt`.

## Components
- Android's own Material 3 controls (Button, NavigationBar, SegmentedButton, FilterChip, Switch) inside the app's
  wrappers (`BigButton`, `BottomBar`, `Segmented`, `Chip`, `AppSwitch`), which keep the tap rules
  (steady-hands pause, touch-to-hear).
- **Histories are timelines** (`Timeline`, `TimelineRow`). Two-column key-value rows (`ValueRow`) only for short
  summaries of fewer than 10 words.
- Everything that looks like a summary opens its own page (History rows, My health measures, doctor page items).
- Every page that loads shows "Getting it ready…" (`Loading`), never a blank screen.
- Charts appear only with two or more points, start where the entries start, and use whole-number axes.

## Words
- Plain, kind, short. Say what happened, not what the app did. No "MedLog" in the UI.
- One wording for one thing everywhere, e.g. **"Food taken instead of feed"** (`FOOD_INSTEAD_WORDS`).
- Every alert card has a real title ("Message from Lakshmi", "A medicine isn't marked as taken") and a **Dismiss**.
- Doctor page and PDF: only what was logged. No "none recorded", no "–", no empty days, no empty columns.
  Keep the existing layout; remove clutter, don't restyle.
- New words need Hindi and Tamil in `app/src/main/assets/i18n/hi.json` and `ta.json` (templates use `{0}`).

## Widgets
- Question at the top, buttons at the bottom; title 22 sp; the status line goes after 10 seconds.
- A tap first asks (Done / Add details / Cancel); nothing is saved on the first tap.
