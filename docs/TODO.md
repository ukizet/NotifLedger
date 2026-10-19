# Review fixes plan

Living doc for the 7-finding code review (2026-10-18). Source review findings
were pasted in-session; this file is the tracking copy across sessions.

**Status: complete (2026-10-18).** All four workstreams landed; QA, CodeReviewer
and PerformanceProfiler passes done; reviewer follow-ups applied and re-verified.
Do not start a fix without updating the checkbox in the same session.

Legend: `[ ]` not started · `[~]` in progress · `[x]` done · `[!]` blocked.

---

## Recon (verified against source, 2026-10-18)

- All 7 findings confirmed by reading the files. Counts match: 9 silent
  `catch (_: Exception)` blocks; 6 in `NotifLedgerApp`, 1 each in
  `NotificationHandler`, `NotificationHelper`, `NotificationSourcesScreen`.
- Bonus latent bug found while reading #6: `JournalWriter.parsePostingAmount`'s
  no-sign fallback regex (`amountWithCurrencyNoSignRegex`) read group 3 of a
  2-group regex — `*184.50 NOK` threw `IndexOutOfBoundsException`, and
  `(184.50 NOK)` fell through to a blank amount. Mutation-verified by QA;
  fixed by #6.
- `MainViewModel`'s own journal I/O is already off the main thread
  (`withContext(Dispatchers.IO)`); the main-thread blocking is the
  `NotifListener` path only (`onNotificationPosted` runs on main, `runBlocking`
  blocks it until the SAF read-modify-write finishes).
- Doc impact: `docs/architecture.md` diagrams describe the current mutex +
  listener-write path and will need updating with the repository extraction;
  `docs/design.md` describes the parser as "first number in title+text".

## Execution order & ownership (AGENTS.md: delegate, one file one owner)

| Phase | Agent | Files (sole owner) | Status |
|---|---|---|---|
| 1 | A — parser | `parser/ParserEngine.kt`, `ParserEngineTest.kt`, `docs/design.md` | [x] |
| 1 | B — journal core | `journal/JournalWriter.kt`, `JournalWriterTest.kt`, new `journal/JournalRepository.kt`, `docs/architecture.md` | [x] |
| 1 | C — notification/VM | `NotifListener.kt`, `NotifLedgerApp.kt`, `NotificationHandler.kt`, `NotificationHandlerTest.kt`, `NotificationHelper.kt`, `MainViewModel.kt`, `SettingsManager.kt` | [x] |
| 2 | D — UI | `ui/MainScreen.kt` (+ new `ui/components/MainBottomBar.kt`, `ui/components/SimulateNotificationDialog.kt`), `ui/components/TransactionForm.kt`, `ui/LogsScreen.kt`, `ui/CategorizationRulesScreen.kt`, `ui/NotificationSourcesScreen.kt` | [x] |
| 3 | E — QATestEngineer | verification only; may add tests | [x] |

A/B/C/D can run in parallel (interfaces frozen below); E runs after all land.
No new dependencies, no DI, `JournalWriter` stays pure.

---

## Fix 1 — parser: body first, title fallback (money-critical)

`[x]`

- `ParserEngine.parse`: `findNumber(text) ?: findNumber(title)` (was
  `"$title $text"` joined search).
- Rewrite test `title contains numbers but text has amount` — body's `99`
  must win, payee still the title. `empty text with number only in title`
  already covers the fallback.
- Update `docs/design.md` §2 / §3 / §4.3 wording ("first number in title+text"
  → "first number in the body, falling back to the title").
- Known residual (accepted): body `"Rema 1000 betalt 184,50"` still picks
  `1000` — inherent to the deliberately dumb heuristic, not in scope.

## Fix 2 + 5 — one `JournalRepository`, off main thread, backup before rewrite

`[x]`

New `journal/JournalRepository.kt` (constructed in `NotifLedgerApp`, manual
wiring):

```kotlin
class JournalRepository(app: Application) {
    suspend fun read(path: String): String?
    suspend fun append(path: String, tx: Transaction): JournalWriteResult?   // UI add; parses entries
    suspend fun appendRaw(path: String, tx: Transaction): Boolean            // listener; no re-parse of whole file
    suspend fun replace(path: String, offset: Int, tx: Transaction): JournalWriteResult?
    suspend fun replaceMany(path: String, updates: Map<Int, Transaction>): JournalWriteResult?
    suspend fun delete(path: String, offset: Int): JournalWriteResult?
}
```

- Owns the mutex (delete `NotifLedgerApp.journalWriteMutex`), runs on
  `Dispatchers.IO` internally, logs failures itself; callers map
  `null`/`false` to the existing UI messages ("Save failed — see Logs").
- Backup: before each overwrite (non-blank existing content only), copy
  current bytes to `filesDir/journal_backup.txt`; on write failure the log
  line names the backup path.
- `NotifListener`: `onCreate` starts a single-consumer worker
  (`Channel<StatusBarNotification>(UNLIMITED)` + `scope.launch(Dispatchers.IO)`
  loop); `onNotificationPosted` and the catch-up scan only `trySend` — main
  looper never blocks, ordering and dedup serialization preserved
  (NotifListener.kt:42 comment still holds). `onDestroy` cancels the scope.
  `handleNotification` becomes `suspend`, dropping its `runBlocking`.
- `NotifLedgerApp` settings getters keep their `runBlocking` (AGENTS.md says
  intentional, and this plan keeps that note true) — they now execute on the
  IO worker instead of main.
- `MainViewModel`: the four read-modify-write blocks (add/edit/delete/bulk)
  and both raw helpers collapse into repository calls;
  `SettingsManager(application)` → `app.settings` (kills a duplicate).
- Not atomic over SAF (no rename on `content://`) — backup is the mitigation,
  note it in a `ponytail:` comment in the repository.
- Update `docs/architecture.md` §1 / §3 / §5 diagrams for the repository.

## Fix 3 — no silent catches, validate rules at save

`[x]`

- Log all 9 silent catches: 6 in `NotifLedgerApp` (fallbacks kept, just
  logged), `NotificationHandler.matchCategory` (bad regex →
  `AppLogger.warn` then skip; first-match-wins unchanged), 
  `NotificationHelper.isListenerEnabled`, `NotificationSourcesScreen`
  label-resolution fallback. `AppLogger` is test-safe (in-memory, no init).
- Rule validation at save: the `match` field in `CategorizationRuleForm`
  gains `isError` / supporting text when `Regex(match)` fails (inline
  `runCatching`), save blocked. File-loaded bad rules are covered by the
  match-time log.

## Fix 6 — JournalWriter

`[x]`

- Merge `postingLine4SpaceRegex` / `postingLineTabRegex` /
  `postingLineLowercaseRegex` into one alternation regex with a comment on
  the why (4-space default, tab, shallow indent + lowercase account).
- Replace the three amount regexes with one:
  `^(-?)([\d.,]+)(?:\s*([a-zA-Z]+))?$` — one code path. New tests:
  junk-prefixed no-sign amounts (`*184.50 NOK`, `(184.50 NOK)`) degrade to a
  blank amount instead of crashing; comma decimals still parse.
- `deleteFromContent`: drop the manual offset-shifting block; rebuild
  content, then `readAll(newContent)` like `replaceInContent`. One approach
  everywhere.

## Fix 7 — housekeeping

`[x]`

- `SettingsManager.DEFAULT_ACCOUNT` / `DEFAULT_CURRENCY` become public
  consts; used by `NotifLedgerApp` fallbacks and `MainViewModel` `stateIn`
  seeds. `TransactionForm.defaultCurrency` default value removed (both call
  sites already pass it).
- Remove the duplicate allowlist check: drop `packageName` / `allowedPackages`
  from `NotificationHandler.processNotification` (the listener's early check
  at NotifListener.kt:33 stays — it guards dedup marking and the documented
  "allowlist later still captures the shade" behavior). Delete the one
  obsolete test, adapt the rest; drop the now-redundant
  `.copy(source = Source.Notification)` in the listener.
- Delete the `Existing tests (preserved)` comment (ParserEngineTest.kt:11);
  sweep for similar leftovers.

## Fix 4 — UI decomposition (phase 2)

`[x]`

Targets: no composable over ~1 screen, ≤3 nesting levels.

- `MainScreen` (371-line function): `MainScreen` → `MainContent`
  (`when`: no-journal / empty / list), `SortControls` — sort dropdown **loops
  `SortOrder.entries`** with the existing `sortLabel` mapping, plus page-size
  control — `TransactionList` + pager, `EditTransactionDialog`; move
  `MainBottomBar` and `SimulateNotificationDialog` to own files.
- Onboarding checklist: render once above the branch instead of twice.
  Micro-change: now also shows on the "no journal selected" screen (it
  self-hides when complete) — the one visible behavior change, flagged.
- `TransactionForm` (262): extract posting-row composable (used in the loop)
  and the details section (payee/currency/payment fields); form keeps state
  + save.
- `LogsScreen` (185): loop the level chips over a data list instead of four
  pasted blocks; move `RawLogView` / `LogEntryCard` out.

---

## Verification (phase 3, QATestEngineer)

`[x]`

- Result 2026-10-18: QA pass 8/8 findings PASS, `testDebugUnitTest
  --rerun-tasks` 213 tests / 0 failures, `assembleDebug` green (all via
  `devenv shell`; no lint configured).
- New/updated tests: parser body-first + title fallback + unparseable-body
  fallback; JournalWriter junk-amount degradation + comma decimals.
  Repository backup stays untested (needs `ContentResolver` → Robolectric, an
  AGENTS.md non-starter); covered by build + manual smoke.
- Reviewer follow-ups applied after the QA pass: repository null-stream read
  guard (a failed read must not be treated as an empty journal), null output
  stream logging, backup path in write-failure logs, listener per-item
  try/catch, raw-journal read routed through the repository, hardcoded regex
  message → `strings.xml`, parser body→title fallback for unparseable body
  numbers, doc drift. Re-verified green.
- Manual device smoke only if requested (device etiquette: no blind input).

## Decisions taken

- Keep the listener's allowlist check and simplify `processNotification`
  instead of the reverse (dedup ordering depends on the early check).
- Onboarding checklist becomes visible on the no-journal screen.
- Beyond-plan refinement accepted during verification: the parser ignores a
  body number that cannot parse and falls back to the title (a junk body token
  like `12.3.4` no longer suppresses a valid title amount).
- Accepted as-is after review: `Channel.UNLIMITED` (correctness over bounded
  memory at this app's event rates; worker has a per-item guard); backup write
  on every overwrite (extra local I/O, deliberate recovery point);
  `MainScreen` orchestrator is 163 lines (soft ~120 target — further extraction
  would mean threading many parameters); SAF URI appears in app-private logs;
  `simulateNotification` read-failure message now says "Save failed" ("see
  Logs" remains the actionable part).

## Deliberately skipped (do not "fix" without a new instruction)

- `MainViewModel.simulateNotification` bypasses categorization rules and
  hardcodes `expenses:unknown`; `NotificationHandler.DEFAULT_EXPENSE_ACCOUNT`
  stays separate — same duplication class but outside the named findings.
- No read-back verification after SAF writes; backup can degrade if the user
  keeps writing after a failed write.
- Parser stays a number heuristic — no per-bank config (design §8).
- Repository pure text transforms run on `Dispatchers.IO` (reviewer low-note);
  fine at current journal sizes — revisit if multi-MB journals appear.

---

# Round 2 — review findings (2026-10-19)

Five findings from the 2026-10-19 review. Recon verified against source in
session; tracking and ownership below. Legend as above.

## Recon (verified 2026-10-19)

- Parser (`ParserEngine.kt`): `\d[\d\s.,]*` first match wins, so `*1234` card
  masks and dates become the "amount"; a date before the amount
  (`15.10.2026 184,50`) makes the whole run unparseable and the amount is
  lost. Listener path still posts a best-effort empty-amount entry
  (`NotificationHandler.processNotification`); the simulate dialog refuses.
- Backup (`JournalRepository.writeContent`): one `journal_backup.txt`,
  overwritten on every write — a bad write followed by another write destroys
  the good copy.
- UI (script, brace matching): 22 functions over 60 lines; indent depth 10 at
  `CategorizationRulesScreen.kt:106`, `NotificationSourcesScreen.kt:239`,
  `StatisticsScreen.kt:179`.
- `MainViewModel`: 418 lines, one activity-scoped instance for all 8 screens.
- `NotifLedgerApp`: 5 `runBlocking` settings getters; only caller
  `NotifListener.handleNotification` is already suspend on `Dispatchers.IO`.

## Execution order & ownership

Phase A and B run sequentially (B changes screens + VMs; A must be green
first). No new dependencies, no DI, `JournalWriter` stays pure.

| Phase | Owner | Files (sole owner) | Status |
|---|---|---|---|
| A | orchestrator | `parser/ParserEngine.kt`, `journal/JournalRepository.kt`, new `journal/JournalBackup.kt`, `NotifLedgerApp.kt`, `AGENTS.md`, `docs/design.md`, `docs/architecture.md` | [x] |
| A | QATestEngineer | `ParserEngineTest.kt`, new `journal/JournalBackupTest.kt`, build + test run | [x] |
| B | orchestrator | `ui/MainViewModel.kt`, 7 new `ui/*ViewModel.kt`, new `journal/JournalSession.kt`, new `ui/Suggestions.kt`, `MainActivity.kt`, `ui/NotifLedgerNavGraph.kt` | [x] |
| B | UIUXDesigner ×4 | fix file groups (below) | [x] |
| C | QATestEngineer | full test run + `assembleDebug` + 60-line/nesting re-measure | [x] |
| C | CodeReviewer | diff review | [x] |

## Fix 1 — parser: currency-adjacent amount, skip masks/dates

`[x]` (QA: 43 parser tests green; mutation check confirmed the mask/date/
adjacency logic is actually exercised)

- `ParserEngine.parse` (signature unchanged): scan number runs; skip `*1234`
  card masks (run preceded by `*`) and dates
  (`\d{1,2}[./-]\d{1,2}[./-]\d{2,4}`); when a date is directly followed by an
  amount in the same run, keep the amount. Prefer the first candidate adjacent
  to a currency marker (kr/NOK/USD/EUR/GBP/SEK/DKK as a whole code, `€/$/£`),
  else the first valid candidate. Body first, title fallback unchanged. Still
  no per-bank config.
- New tests: `Kjøp med kort *1234: 184,50 kr` → 184.50; `15.10.2026 Kjøp
  184,50 kr` → 184.50; `15.10.2026 184,50` → 184.50; `Kjøp hos Rema 1000 for
  184,50 kr` → 184.50; mask-only text → null.
- Docs: `design.md` §2/§3/§4.3/§8, `architecture.md` §1, `AGENTS.md` parser
  bullet.

## Fix 2 — backups: keep 10 timestamped copies

`[x]` (QA: 4 JournalBackup tests green; repository write path covered by build
+ unchanged abort-on-backup-failure)

- `JournalBackup.kt` (new, pure): `fileName(timestamp)` +
  `namesToDelete(names, keep)`; JVM-testable without Robolectric.
- `JournalRepository`: write `backups/journal_backup_<yyyyMMdd_HHmmss_SSS>.txt`
  before each overwrite, prune to `MAX_BACKUPS = 10`; abort the journal write
  if the backup fails (unchanged). Stale `journal_backup.txt` left alone.
- Docs: `design.md` §4.9, `architecture.md` §1/§8.

## Fix 3 — UI decomposition

`[x]` (verified by orchestrator: 230 functions in `app/src/main`, 0 over 60
lines; deepest nesting 6; `SwipeToDelete.kt` needed no change — already under
the limit)

- Target: 0 functions over 60 lines in `app/src/main`, refactored-file nesting
  ≤6 (Compose lambda depth; was 10). Pure extraction — no visual/behavior
  change; `EntryList`, `isInSwipeStartZone`, `crossesDeleteThreshold`,
  `updatedDrag` keep their signatures (tested).
- Owners: orchestrator — `NotifLedgerNavGraph`; UIUXDesigner A —
  `MainScreen.kt`, `components/SimulateNotificationDialog.kt`,
  `components/BulkChangeDialog.kt`, `components/TransactionRow.kt`;
  UIUXDesigner B — `components/TransactionForm.kt`, `components/DateField.kt`,
  `components/FilterRow.kt`, `components/SwipeToDelete.kt`,
  `components/DonutChart.kt`; UIUXDesigner C1 — `QuickAddScreen.kt`,
  `SettingsScreen.kt`, `StatisticsScreen.kt`; UIUXDesigner C2 —
  `CategorizationRulesScreen.kt`, `NotificationSourcesScreen.kt`,
  `LogsScreen.kt`, `RawJournalScreen.kt`.
- `docs/coding_standarts.md` unchanged.

## Fix 4 — MainViewModel per screen

`[x]` (screens re-wired; `assembleDebug` + tests green; compiler warnings from
the old dead `onNavigateToRawJournal`/`onNavigateToCategorizationRules`/
`onNavigateToLogs` params cleaned up — `MainBottomBar` navigates directly)

- `journal/JournalSession.kt` (new, constructed in `NotifLedgerApp`): in-memory
  `entries`/`isLoading` StateFlows, `suspend refresh()` (reads the journal via
  `JournalRepository`), `apply(entries)` after a successful write. Not a cache:
  the file stays the source of truth.
- Per-screen VMs created per back-stack entry in `NotifLedgerNavGraph`
  (`MainActivity` stops creating one): `MainViewModel` (list/filters/sort/
  paging/edit/bulk/simulate/suggestions/journal path), `QuickAddViewModel`,
  `LogsViewModel` (owns the `AppLogger` subscription), `RawJournalViewModel`,
  `SettingsViewModel`, `CategorizationRulesViewModel`,
  `NotificationSourcesViewModel`, `StatisticsViewModel`. Explicit setters
  replace `viewModel.settings` access. Shared payee/account derivation in
  `ui/Suggestions.kt`.

## Fix 5 — drop `runBlocking` from `NotifLedgerApp`

`[x]` (getters are `suspend`; `assembleDebug` green)

- 5 getters → `suspend`; `runBlocking` import deleted; `NotifListener` already
  calls them from its suspend worker. `getCategorizationRules()` stays sync.
- Docs: `AGENTS.md`, `architecture.md` §1.

## Verification

`[x]`

- [x] `devenv shell -- ./gradlew testDebugUnitTest` green (223 tests, 0 failures)
- [x] `devenv shell -- ./gradlew assembleDebug` green (0 warnings)
- [x] function-size/nesting script: 0 functions >60 lines (230 functions;
  QA's comment-aware matcher: 296 functions, 0 over 60; max nesting 6 in
  refactored files)
- [x] CodeReviewer pass; findings applied and re-verified (see below)

## Phase C review findings applied (2026-10-19)

- Quick-add write could be cancelled by the back-stack pop (per-screen VMs):
  moved screen-launched writes to `NotifLedgerApp.writeScope` (QuickAdd,
  Settings, CategorizationRules, NotificationSources).
- Parser: `*1234 184,50 kr` was one number run and got discarded wholesale;
  mask digits are now stripped and the remainder kept when it has a decimal
  separator or is a single currency-adjacent group (5 new tests, mutation-
  checked).
- QA counterexample caught in re-verification: `*1234 5678 9012 kr` posted
  concatenated mask digits (`56789012`); the guard now rejects multi-group
  remainders.
- `JournalBackup.namesToDelete` now only counts files matching the exact
  timestamped name pattern (1 new test).
- `JournalSession.refresh` no longer clears `isLoading` before the read
  finishes (cold-start flash of the empty state).
- Manifest: `filesDir/backups` excluded from Android cloud backup and device
  transfer (`backup_rules.xml`, `data_extraction_rules.xml`) — 10 full
  journal copies stay internal-only.
- Docs: AGENTS.md repository boundary, architecture §7 Statistics node, §8
  cache wording, design §4.4 rules timing, §4.9 backup exclusion, listener
  comment.
- Residual (accepted): `MainViewModel` write paths stay on `viewModelScope` —
  Main is the root destination, so it only dies with the app, not on
  navigation.
