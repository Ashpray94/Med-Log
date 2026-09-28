# Code map (app/src/main/java/com/suryaprakash/medlog)

| Area | Files |
|---|---|
| App start, links, screens by route | `MainActivity.kt` (routes → screens, deep links `medlog://…`), `MedLogApp.kt` (startup jobs), `ui/Nav.kt` (routes) |
| Theme, colours, sizes | `ui/Theme.kt` |
| Shared components | `ui/Components.kt` (Screen, BigButton, Card, Group, Timeline, AppSheet, BottomBar, Segmented, Chip…), `ui/Forms.kt` |
| Home (person) | `ui/screens/HomeScreen.kt` (DayCard, StatusBanner, DaySheet, PendingDetails) |
| How are you feeling | `ui/screens/TellScreen.kt`, `clinical/Interview.kt` (questions), `clinical/Triage.kt` (danger rules), `clinical/Describe.kt`, assets `clinical/catalogue.json` |
| Speak it all | `ui/screens/SpeakAll.kt`, `nlu/` |
| Medicines and feeds | `ui/screens/MedsScreens.kt`, `MedAddFlow.kt`, `TodayMeds.kt`, `meds/Scheduler.kt`, `meds/DoseAlert.kt`, `meds/DoseActivity.kt`, `data/DoseOutcome.kt` |
| Food, water, readings, feeds | `ui/screens/FoodReadings.kt`, `FoodPick.kt`, `NutritionScreen.kt`, `nutrition/` |
| History | `ui/screens/NotesScreens.kt` |
| My health and measure pages | `ui/screens/HealthScreen.kt` (ReportsScreen, MeasureScreen) |
| Doctor page and PDF | `ui/screens/DoctorPage.kt`, `DoctorScreens.kt`, `doctor/DoctorNote.kt`, `doctor/Pdf.kt`, `doctor/Summary.kt` |
| Helper's phone | `ui/screens/HelpScreens.kt` (HelperHome, LatestMessage, HelperChat, PairScreen), `ui/screens/DoseChoices.kt`, `care/Care.kt` (HelperCare, HelperDose) |
| Family, SOS, alerts | `help/` (Nearby, Relay, FamilyChat, Alerts, Sos, AlertActivity, Wording) |
| Records and sync | `data/Db.kt`, `data/Repo.kt`, `data/Sync.kt`, `data/Occurrences.kt`, `data/People.kt` |
| Widgets | `widget/Widget.kt`, `widget/SmallWidgets.kt` |
| Updates | `Updater.kt`, `.github/workflows/release.yml` |
| Translations | `app/src/main/assets/i18n/hi.json`, `ta.json` |
| Tests | `app/src/test/java/com/suryaprakash/medlog/` (`DataIntegrityTest`, `ColourRulesTest`, `InterviewTest`, `TriageTest`, `Shots`…) |
