# Starting new chats

Long chats get worse: early details get squeezed out, and the model starts to forget rules and repeat mistakes.
So each piece of work gets **its own new chat**, and what must survive between chats lives in files, not in a chat.

- `CLAUDE.md` (repo root) is loaded automatically in every chat on this repo: rules, commands, pointers.
- `docs/context/*.md` hold the details: UI rules, data rules, workflow, code map, status.
- Each file below starts one chat. Open a **new chat on this repo, branch `main-mxp0m4`**, paste the prompt in the
  file's "Paste this" block, and add your screenshots or notes.
- Every chat ends by updating `docs/context/STATUS.md`, so the next one starts from the truth.

## Which chat for what

| File | Use it when |
|---|---|
| `01-bug-report.md` | Something is wrong on the phone (the most common one). One bug or one screen per chat. |
| `02-phone-check-2.13.md` | Going through the 2.13 phone checklist and reporting what you found. |
| `03-helper-mode.md` | Anything on a helper's phone: reminders, messages, other helpers, pairing. |
| `04-doctor-page-pdf.md` | The doctor page, its PDF, printing and sharing. |
| `05-data-integrity.md` | Counts look wrong, entries are missing or doubled, phones disagree. |
| `06-design-pass.md` | A visual pass over one area (spacing, colour, hierarchy), screen by screen. |
| `07-open-issues.md` | Working through the open issues list (O1–O22), a few at a time. |
| `08-translations.md` | Hindi or Tamil wording, missing translations, long words breaking layouts. |
| `09-release.md` | Publishing a new version for the in-app update. |
| `10-new-feature.md` | Something new: it's planned first, then built. |

## Tips
- One topic per chat. When a chat has done its job, start a fresh one for the next thing, even on the same day.
- Attach screenshots: they're the fastest way to show what's wrong.
- If a chat starts repeating a mistake or forgets a rule, ask it to update `STATUS.md`, then start a new chat.
- Ask for a release only in `09-release.md` (or at the end of a chat, once its work is tested).
