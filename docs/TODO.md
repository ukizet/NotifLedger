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
