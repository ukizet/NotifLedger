# NotifLedger — Design Documentation

## 1. Purpose

An Android app that listens to bank/payment notifications, extracts
transaction data from them, and writes properly formatted
[hledger](https://hledger.org) journal entries automatically — no approval
step. Anything that *doesn't* generate a notification (cash, crypto, informal
transfers, whatever) goes through a separate, deliberately minimal manual-entry
flow.

Core stance: **if the bank already told you it happened, don't make the human
confirm it again. If nothing told you, make saying so take five seconds.**

## 2. Why this shape

- hledger is plain-text, append-only, diffable, git-friendly. Nothing here
  should compromise that — no binary DB as source of truth, no cloud sync
  that touches the ledger directly.
- Bank notifications are semi-structured noise: format varies by bank, by
  app version, by locale. Rather than hardcoding parsers per bank, the app
  takes a simple heuristic approach: find the first number (amount) and use
  the notification title as the payee. This is surprisingly robust and
  eliminates the need for per-bank configuration.
- Reviewing every single notification-sourced transaction defeats the point —
  the whole reason to build this is to stop typing things by hand. The
  tradeoff is accepting that parsing needs to be trustworthy and mistakes need
  to be *easy to find and fix after the fact*, not prevented up front by a
  gate.

## 3. Data flow

**Path A — notification-triggered (automatic, no approval)**

```
Notification posted (bank app)
        │
        ▼
NotificationListenerService captures it
        │
        ▼
Filter by allowlist: is this app selected in notification sources?
        │
        ▼
Simple parse: find the first number → amount, use title → payee
        │
        ▼
Categorization pass (rule-based: payee patterns → account)
        │
        ▼
Money-side account filled from default payment account
        │
        ▼
Append to journal immediately — no gate, no queue
```

If parsing is uncertain (no number found), the entry still posts — best-effort,
using whatever was extracted, with a fallback account like `expenses:unknown`.
Nothing waits. Fixing it later is just editing that one line, same as fixing any
other transaction, right on the main list (see §4.5).

**Path B — manual entry (cash, crypto, anything with no notification)**

```
User opens app → quick-add screen
        │
        ▼
Amount, payee, account — account defaults to default payment account,
overridable per entry (e.g. cash instead of card)
        │
        ▼
Append to journal immediately
```

Both paths write to the same journal, immediately, no approval step either
way. The only thing the main screen ever asks of the user is to glance at
what's landed and fix anything that's wrong — not approve anything before it
lands.

## 4. Components

**4.1 Notification capture**
- `NotificationListenerService` on Android, filtered to a user-selected
  allowlist of package names (bank apps, Vipps, etc.) — not global capture.
- The allowlist is configurable from the main screen via a dedicated screen
  ("Notification sources", bell icon on the bottom bar). Users select apps
  through a searchable dialog that lists all installed apps.
- Store only what's needed to parse (title, text, package, timestamp), then
  let the original notification go. No notification content is retained
  longer than needed to produce a draft transaction.

**4.2 Default payment account**
- A single setting: which account (e.g. `assets:bank:checking`) fills the
  money-side leg of a transaction when nothing more specific is known.
- Used by both paths: notifications rarely name a specific account, so this
  is what completes the double-entry; manual quick-add pre-fills it too,
  since most manual entries are still "money left this account" even when
  cash/crypto is the other leg.
- Changeable any time in Settings; changing it only affects new entries, not
  a retroactive rewrite of the journal.
- If someone has genuinely multiple accounts in regular use (several cards,
  say), this is a starting default per entry, not a hard constraint — still
  editable per transaction wherever an account field appears.

**4.3 Parser engine**
- No per-bank configuration needed. The parser simply:
  - Finds the **first number** in the notification title+text → amount
  - Uses the **notification title** as the payee/description
  - Creates a two-posting transaction (expense account + auto-balanced money account)
- If no number is found, a best-effort entry is posted with empty amount
  (auto-balanced on write) and the title as payee — never silently dropped.
- Norwegian comma decimals (184,50) are handled transparently.

**4.4 Categorization**
- Simple ordered rule list: payee substring/regex → account.

  ```yaml
  - match: "REMA|KIWI|COOP"
    account: expenses:groceries
  - match: "Spotify"
    account: expenses:subscriptions:music
  - default: expenses:uncategorized
  ```
- Rules are editable in-app via the Categorization rules screen (tags icon
  on the bottom bar).
- Changes take effect immediately for the next notification/entry — no
  rebuild, no restart.

**4.5 Main page — formatted transaction list**
- The home screen shows recent transactions rendered nicely — date, payee,
  amount, account — not raw journal text. This is the everyday view.
- Each row has an **Edit** button right on it. Tap it, the entry opens for
  editing directly, save writes the correction back into the journal in
  place. No separate picker screen — the list itself is the picker.
- Whether an entry came from a notification or manual quick-add, it's
  rendered and editable the same way.
- Also hosts the entry points to the less-frequent screens: categorization
  rules, notification sources, raw journal view, and settings — all one tap
  away on the bottom bar.

**4.6 Notification sources screen**
- A dedicated screen (bell icon on the bottom bar) showing all selected apps.
- Tap "Add app" to open a searchable dialog listing all installed apps on
  the device. Filter by typing the app name or package name.
- Each selected app shows its label and package name, with a remove button.

**4.7 Raw journal view**
- A separate, secondary screen (accessible from the main page — list icon
  on the bottom bar) that shows the actual journal file as plain text,
  unformatted. This is the escape hatch for anything the formatted view
  can't represent well, or just for trusting-but-verifying what's actually
  on disk.
- Read-only here; edits still happen through §4.5 or an external editor —
  keeps the app from needing a full text-editing UI for raw hledger syntax.

**4.8 Manual quick-add**
- Separate, always-available entry point for cash, crypto, and anything else
  with no notification trail.
- Minimal fields: amount, payee/description, account (pre-filled from the
  default payment account, §4.2, but overridable).
- Currency and date default to "now" / your base currency and are editable.
- No distinction in the journal between how an entry arrived — manual entries
  look identical to auto-posted ones once written, and are editable the same
  way through §4.5.

**4.9 Journal writer**
- Appends in standard hledger format:

  ```
  2026-07-12 Rema 1000
      expenses:groceries              184.50 NOK
      assets:bank:checking           -184.50 NOK
  ```
- Auto-balances: if exactly one posting has an empty amount, the negative
  total of the other postings is computed and used.
- Writes to a file path the user points at (Storage Access Framework) — the
  app never maintains a separate in-app copy of the data that could drift
  from what's on disk.

## 5. What "semi-automatic" means here, concretely

| Has a notification | Doesn't have one |
|---|---|
| Auto-parsed, auto-categorized, auto-posted — no approval | Manual quick-add, a few taps, no form-filling |
| Low-confidence parses still post (best-effort), never dropped | N/A |
| Fixed via the main list: tap Edit on the row, correct, save | Same |
| Rules fixable directly from the main page (§4.4) | N/A — manual entries don't need rules |

The split is drawn by *source of truth*, not by transaction type: a bank
already confirming something happened is enough to trust; anything without
that confirmation needs a human to say so, but saying so should be trivial.
Correction, in both cases, happens after the fact, on the same screen, the
same way.

## 6. Open questions worth deciding early

- **Account structure**: fixed chart of accounts, or inferred/created
  on the fly from categorization rules?
- **Multi-currency**: how much of hledger's commodity/cost handling to
  surface in the UI vs. keep hidden until needed?
- **Split transactions**: notifications rarely carry split info (e.g. this
  purchase was half groceries, half household) — worth a quick-split UI on
  the edit screen, or leave that to manual journal edits?
- **How far back does the main view scroll?** Last N entries, last N days,
  or does it just tail the file live and let the user scroll into history?
- **Multiple default accounts**: is one global default payment account
  enough, or does it need to be per-notification-source (e.g. one card's
  notifications default differently than another's)?
- **Permission model**: `NotificationListenerService` is a fairly sensitive
  Android permission — worth documenting clearly for anyone else who'd use
  this, even if it's just for you.

## 7. Tech stack

- Kotlin, Jetpack Compose, Material3
- `NotificationListenerService` for notification capture
- Storage Access Framework (SAF) for journal file access — no filesystem
  permissions needed
- DataStore Preferences for app settings
- SnakeYAML for categorization rule persistence
- Categorization rules as YAML files in app's internal storage
- AMOLED black theme, auto-switches with system dark mode

## 8. Non-goals (for now)

- No bank API / Open Banking integration — notifications only, by design,
  to avoid credential/OAuth surface area.
- No cloud backend. Sync is whatever the user already uses for the journal
  file (Syncthing, git, etc.), not something this app owns.
- No auto-categorization via ML — rule-based stays legible and debuggable.
- No per-bank parser rules — the simple heuristic (first number = amount,
  title = payee) is enough for most bank notifications.
- No DI framework — manual construction via `Application` class.
- No Room database — all state is in the journal file or DataStore.