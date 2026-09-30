# Dev acceptance checks (pending)

Install the `MedLog-dev` APK from the latest successful audit-branch Actions run. It has a separate application ID. Do not install the prod artifact onto the real phone. No test below has been performed on a device yet.

1. Upgrade a dev copy built from shipped 2.14.1 with representative backed-up records; confirm startup and records. Repeat with draft version-4 data. Preserve originals.
2. Pair two dev phones. Create different medicine IDs locally; verify a synced dose points to the correct medicine. Verify a helper selects the correct patient and another patient's records never appear.
3. Add, edit, remove and restore a note on each side. Disconnect one phone, make changes, restart both, reconnect. Check eventual equality and exactly one notification per remote change. Repeat with more than 400 records sharing a timestamp.
4. Deliver repeated and older packets; records must not duplicate or roll back. Interrupt receipt and verify no partially applied packet. Test group links and receiver-local photos.
5. Test nearby transport with internet disabled in both directions. Existing fallback is not yet proof of instant local-network push.
6. Appointment edit/delete sync, safe mirrored dose actions and isolated live chat remain implementation blockers; do not claim these checks pass from current code.
7. At maximum font size in English/Hindi/Tamil, inspect every self-mode screen: pictorial choices, spoken question/explanation, no disease names, no clipped action, one clear next step, forgiving undo. Patient must never encounter helper-only chat tools.
8. Trigger each reminder/alert and exercise all actual actions, including lock screen, repeated taps, dismissed notification, offline helper and denied permissions. Verify loud sustained-C sound on hardware.
9. Exercise feedback with text alone, optional screenshot and optional annotation; exercise helper CRUD and mode-specific settings.
10. Licensed clinician reviews clinical questions, branching, thresholds and report examples. Until then content stays AI-assisted and unreviewed. Completeness must reach zero without loose tags or waived gaps before release.

Design references for validation: [Apple accessibility](https://developer.apple.com/design/human-interface-guidelines/accessibility), [Apple larger-text criteria](https://developer.apple.com/help/app-store-connect/manage-app-accessibility/larger-text-evaluation-criteria), [NHS interior signage](https://www.england.nhs.uk/nhsidentity/examples/nhs-interior-signage/). Test large text without clipping or losing actions; use readable contrast and recognisable symbols/pictures. These principles inform the checks above; applying them does not prove the current app accessible.

Environment constraint: the standard dev flavor disables internet Relay and the updater. Use it for local database, UI and nearby tests; it cannot establish internet catch-up or live relay messaging. Those checks require an explicitly isolated test relay configuration and test accounts/devices before production approval. Do not turn on the production relay in dev just to make a check pass.
