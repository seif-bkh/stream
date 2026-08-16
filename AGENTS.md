# AGENTS.md

Rules for the **Dump** Android app. Read before every task. Apply to every change. No exceptions.

---

## Plan first. Always.
1. Before touching code, write a short plan: goal, files you'll touch, steps, risks.
2. Show the plan to the user and wait for approval before executing.
   - Exception: trivial fixes (typos, one-line bugs) may proceed directly — still state what you're doing first.
3. Keep plans short. Goal / files / steps / risks. No essays, no restating the spec.
4. If the plan reveals the task is bigger than expected, say so and re-plan instead of silently expanding scope.

## When ambiguous — ask, never guess
Stop and ask the user when:
- More than one valid approach exists and the choice matters.
- Behavior isn't specified (edge case, empty state, error case).
- The request conflicts with any rule below.
- Scope is unclear (which screen, which flow, how much to change).
- You're about to change timestamp/save/recovery logic — confirm intent first.

Do not pick an interpretation silently. One clarifying question beats a wrong implementation.

## Before you write code
1. Plan approved (per above). Read the file you're changing. Don't guess its contents.
2. Change only what was asked. No drive-by refactors, no extra features.

## Never do these
- Never add a `Service`, `ForegroundService`, `WorkManager`, `AlarmManager`, or persistence `BroadcastReceiver`.
- Never add a Save button, menu item, or confirm dialog on the capture path.
- Never add folders, tags, titles, categories, or sync.
- Never add a splash screen, onboarding, or a home screen. Launcher activity is Capture.
- Never write to the DB on a keystroke.
- Never stamp the timestamp at app open.
- Never delete a non-blank draft unless a real save just committed it. Whitespace-only scratch state is discarded without a DB write. Stored entries may only leave the Log through the confirmed recoverable Trash flow; permanent deletion is only available from Trash with confirmation.

## Always do these
- Capture opens with focus in the field and keyboard shown, every time (launch, widget/shortcut, FAB).
- Timestamp is stamped at the **first keystroke** of a new entry, fixed until real save, and renders exactly `THU AUG 13 · 14:32:07`.
- Text change → debounce 250–300ms → write raw string to `filesDir/draft_buffer.txt` + save first-keystroke millis to prefs. Nothing else.
- 2s idle **or** `onStop()` → commit a non-blank draft to DB with the stamped timestamp → update `last_real_save_timestamp` → delete draft file + pref.
- Empty or whitespace-only drafts never reach Room. Discard new blank captures; if an existing entry is cleared, restore its previous saved text.
- On every launch: if a draft file is non-blank and its time > `last_real_save_timestamp`, restore text + original timestamp and show "recovered unsaved text". Otherwise delete the blank or already-saved draft.
- Swipe right = Capture → Log. Swipe left = Log → Capture. Never invert.
- Opening a Log entry edits that same entry; keep its original first-keystroke timestamp through every later save.
- Moving an active entry to Trash requires confirmation, remains recoverable, and resets Capture if that entry was open.
- Import merges; skip any entry whose timestamp already exists, including entries already in Trash.

## Style rules (apply to any UI you touch)
- Background `#F5F2EB`, text `#181818`, muted `#6B6560`, accent `#C17A2E`.
- Accent only for: status indicator, FAB, active search/state. Nowhere else.
- Monospace for timestamps/times/dividers/status. Sans for entry text.
- Flat. No cards, no elevation, no borders, no animations beyond the swipe.

## Log screen invariants
- Reverse chronological, grouped by `Today` / `Yesterday` / `Mon Aug 12`.
- Row = monospace `HH:MM` + entry text + a trash icon. Tapping the row opens it for editing with its original timestamp.
- The trash icon opens a `Move to trash` / `Cancel` confirmation; it never deletes immediately.
- Search has two modes: substring filter, and jump-to-date that **scrolls to the divider** (not filters).
- FAB bottom-right opens a fresh empty Capture. Trash and Settings buttons stay in the top corner.
- Trash is minimal: restore directly, permanently delete one entry after confirmation, or empty all Trash after a separate confirmation.

## Settings screen invariants
- Exactly two actions: Export, Import. Add nothing else.

## Before you report done
- [ ] Plan was shown and approved before execution
- [ ] Ambiguities were asked about, not guessed
- [ ] No service / no save button / no titles-tags-folders added
- [ ] Keyboard still opens instantly on Capture
- [ ] Timestamp still fires on first keystroke, format unchanged
- [ ] Draft write is still cheap (raw string, no parsing)
- [ ] Real save still fires on 2s idle **and** `onStop()`
- [ ] Empty and whitespace-only notes never reach Room
- [ ] Clearing an existing entry restores its previous saved text
- [ ] Recovery path still works if the app is killed mid-typing
- [ ] Swipe right still opens Log and swipe left still returns to Capture
- [ ] Reopened entries still save with their original timestamp
- [ ] Trash remains recoverable until an explicit permanent-delete confirmation
- [ ] Project builds
