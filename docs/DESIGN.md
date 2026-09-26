# MedLog design rules

These rules hold for every screen. When a screen breaks one, the screen is wrong, not the rule.

## 1. One screen, one job

Before building a screen, write down its job in one sentence and name the most important thing on it. Everything
else on the screen serves that thing or goes somewhere else.

| Screen | Its job | The most important thing |
|---|---|---|
| Home | Start the day's tasks | "How are you feeling?" |
| How are you feeling | Name the problem | Your usual problems, then search |
| A question | Answer it | The question, then the answers |
| Family | Reach family now | Your messages to send |
| History | See what happened | The list, newest first |
| Doctor page | Show and share it with the doctor | The page, then Share |
| Helper home | Answer the person | Their latest message and a reply |
| Helper alert | Respond within 30 s | "I'm coming" |
| Setup page | Answer one question | The question |

## 2. Pick the pattern that fits the job

Don't reuse one pattern everywhere. Choose by what the person is doing:

- **Answering a question → task page (`FlowScreen`).** The task's name sits small at the top, with round Back
  and Close buttons. The question uses the largest words on the screen. Then the answers, and one main button
  pinned at the bottom with a quiet second choice under it. One question per page (NHS "one thing per page").
- **Choosing one or many → `Choice` rows.** The mark sits on the left, round for pick-one and square for
  pick-many, and the whole row is tappable. A chosen row gets an accent outline, a light accent fill and a filled
  mark with a tick. An unchosen row is white with a grey outline. "Chosen" never depends on colour alone.
- **Choosing among pictures → equal tiles (`Tile`).** Every tile is the same size and weight. A chosen tile
  gets a tick in the corner.
- **Seeing facts → grouped rows (`Group` + `ValueRow`).** The label goes on the left and the value on the
  right, one grey surface per group, and a section name with a text action ("Change", "See all").
- **One number that matters → `Metric`.** A small label and time, then the value large with its unit, then
  one line of meaning ("Normal", "Higher than usual").
- **Explaining something new → points (`Point`).** An icon, a bold line and one quiet line under it. Use at
  most four.
- **Yes / No → `YesNo`.** Yes is green with ✓ and No is white with ✗. They are the same size and never look alike.

## 3. Simple, but detailed on request

Show the least that answers the screen's job. Put detail one tap deeper, in the row it belongs to, and never as
more cards on the same screen. A row says "Headache · 3 today"; tapping it shows the times, how bad and what helped.

## 4. Colour: 60 / 30 / 10

- **60% white:** the screen background.
- **30% greys:** surfaces, borders, secondary text.
- **10% one accent (teal):** the main action, "chosen" and the current tab.
- **Red** means danger or SOS only. **Green** means Yes, OK or taken only. **Amber** means "watch" only.
- Icons are grey (ink). A colour must mean something.

## 5. Type: a clear hierarchy

From largest to smallest: the question, then the screen title, then section headings, then body text, then small
notes. There is one question per screen at most. Body text is 18 sp or more, and 22 sp or more in Large mode.

## 6. Built for older hands and eyes

- Touch targets are at least 60 dp (74 dp in Large). Primary buttons span the full width.
- Text contrast is at least 4.5 : 1, and 7 : 1 for body text.
- Every icon has a word next to it. Never use icon-only buttons, except Back and Close, which are labelled for
  screen readers.
- Taps only: no swipes, long-presses or hidden gestures. A tap acts on release, and repeat taps within 0.6 s are
  ignored (for tremor).
- Keep typing to a minimum. Offer suggestions first, then search. The Speak button uses the phone's own speech
  typing.
- Every screen has a way back. Nothing times out on the person. Undo lasts 3 s.
- Read aloud is optional. It is asked about in setup, lives in the bottom navigation, or is a round corner
  button where there is no navigation.

## 6b. Icon boxes

Anything drawn inside an icon box (a glyph, a picture, an "Aa" sample) takes at most half of the box, centred, with
at least a quarter of the box as clear space on every side. Every icon background is the same shape: a rounded
square (corner radius about 28% of its size). People's initials are the only circles.

## 7. Navigation

The bottom navigation is always there on the person's phone, in the same places: Home, History, SOS, Family, and
Read aloud. No screen repeats it with its own Home button. A screen's main action is pinned above the navigation,
so it never scrolls away.

## Sources

- Nielsen Norman Group, "Usability for senior citizens": https://www.nngroup.com/articles/usability-for-senior-citizens/
- Mobile health app design for older adults, a review (2023): https://pmc.ncbi.nlm.nih.gov/articles/PMC10557006/
- NHS design system, radios and checkboxes: https://service-manual.nhs.uk/design-system/components/radios ·
  https://service-manual.nhs.uk/design-system/components/checkboxes
- NHS accessibility guidance: https://service-manual.nhs.uk/accessibility/design
