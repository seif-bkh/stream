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
- Never delete user text or drafts unless a real save just committed them.

## Always do these
- Capture opens with focus in the field and keyboard shown, every time (launch, widget/shortcut, FAB).
- Timestamp is stamped at the **first keystroke** of a new entry, fixed until real save, and renders exactly `THU AUG 13 · 14:32:07`.
- Text change → debounce 250–300ms → write raw string to `filesDir/draft_buffer.txt` + save first-keystroke millis to prefs. Nothing else.
- 2s idle **or** `onStop()` → commit draft to DB with the stamped timestamp → update `last_real_save_timestamp` → delete draft file + pref.
- On every launch: if draft file is non-empty and its time > `last_real_save_timestamp`, restore text + original timestamp and show "recovered unsaved text". Otherwise delete the draft.
- Swipe left = Capture → Log. Swipe right = Log → Capture. Never invert.
- Import merges; skip any entry whose timestamp already exists.

## Style rules (apply to any UI you touch)
- Background `#F5F2EB`, text `#181818`, muted `#6B6560`, accent `#C17A2E`.
- Accent only for: status indicator, FAB, active search/state. Nowhere else.
- Monospace for timestamps/times/dividers/status. Sans for entry text.
- Flat. No cards, no elevation, no borders, no animations beyond the swipe.

## Log screen invariants
- Reverse chronological, grouped by `Today` / `Yesterday` / `Mon Aug 12`.
- Row = monospace `HH:MM` + entry text. Nothing else.
- Search has two modes: substring filter, and jump-to-date that **scrolls to the divider** (not filters).
- FAB bottom-right opens a fresh empty Capture. Settings button top corner.

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
- [ ] Recovery path still works if the app is killed mid-typing
- [ ] Swipe directions unchanged
- [ ] Project builds
