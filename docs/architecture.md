# NotifLedger — Codebase Diagrams

Mermaid maps of how the app works. For the *why* behind the design, read
[design.md](design.md); for code rules, [coding_standarts.md](coding_standarts.md).

## 1. Component overview

```mermaid
flowchart TB
    subgraph system["Android system"]
        bank["Bank / payment apps"]
        framework["NotificationListenerService<br/>framework callback"]
    end

    subgraph app["NotifLedger (single process)"]
        application["NotifLedgerApp<br/>Application: settings, rules cache"]
        listener["NotifListener<br/>NotificationListenerService"]
        handler["NotificationHandler<br/>allowlist check + categorization"]
        parser["ParserEngine<br/>first parseable number = amount (body first, title fallback)"]
        helper["NotificationHelper<br/>persistent listening status notification"]
        dedup["NotificationDedup<br/>processed notification ids"]
        activity["MainActivity<br/>Compose + NavHost"]
        vm["MainViewModel<br/>all UI state"]
        repo["JournalRepository<br/>single journal I/O point:<br/>mutex + Dispatchers.IO + backup"]
        writer["JournalWriter<br/>pure text: format / readAll / replace / delete"]
    end

    subgraph storage["Storage"]
        journal["hledger journal file<br/>content:// URI via SAF"]
        datastore["DataStore Preferences<br/>journal path, accounts, sources, sort, page size"]
        files["App-internal filesDir<br/>rules/categorization.yaml<br/>filters/filters.yaml<br/>logs/notifledger.log<br/>journal_backup.txt"]
    end

    bank --> framework --> listener
    listener --> handler --> parser
    listener --> dedup
    listener --> repo
    vm --> repo
    repo --> writer
    repo -->|read + write via ContentResolver| journal
    listener -->|runBlocking reads| application
    application --> datastore
    application -->|loads rules at startup| files

    activity --> vm
    vm --> datastore
    vm -->|rules + filters YAML| files
    listener -.->|posts status notification| helper
```

No DI framework and no database: `NotifLedgerApp` constructs `SettingsManager`,
the notification path reads settings from it synchronously (`runBlocking`), and
the only durable stores are the journal file, DataStore, and a few YAML/log
files. Both the listener and the ViewModel reach the journal only through
`JournalRepository`, which serializes every read-modify-write with its own
mutex, runs the ContentResolver work on `Dispatchers.IO`, and keeps a
`journal_backup.txt` recovery copy before each write.

## 2. Notification capture flow (automatic path)

```mermaid
sequenceDiagram
    participant Bank as Bank app
    participant System as Android framework
    participant L as NotifListener
    participant Q as in-memory Channel
    participant W as IO worker coroutine
    participant D as NotificationDedup
    participant H as NotificationHandler
    participant P as ParserEngine
    participant R as JournalRepository
    participant SAF as Journal file (SAF)
    participant DS as DataStore

    Bank->>System: posts notification
    System->>L: onNotificationPosted(sbn)
    L->>Q: trySend(sbn)
    Note over L: returns immediately — the main looper never blocks

    Q->>W: sbn
    W->>W: shouldProcess(package in allowed sources)?
    Note over W: not allowlisted → drop, nothing recorded

    W->>W: journal URI set?
    Note over W: no path configured → warn + drop

    W->>DS: processedNotificationIds
    W->>D: add(postTime|key)
    alt id already seen
        D-->>W: null → drop (no duplicate entry)
    else new id
        D-->>W: updated raw ids (kept, max 200)
    end

    W->>W: extract android.title / android.text
    W->>H: processNotification(title, text, rules, ...)
    H->>P: parse(title, text, category account, default account, currency)
    alt number found
        P-->>H: Transaction(amount, payee = title)
    else no number
        P-->>H: null
        H->>H: createBestEffortTransaction(...)
        Note over H: empty amounts, still posted — never dropped
    end
    H-->>W: Transaction

    W->>R: appendRaw(journalUri, tx)
    R->>SAF: read existing content + backup + write content ("wt")
    W->>DS: persist updated dedup ids
```

Notes:

- `onNotificationPosted` only enqueues into an in-memory `Channel`; ONE IO
  worker coroutine consumes it, so the main looper never blocks. Dedup,
  parsing, and `JournalRepository.appendRaw` all run in that worker
  (`handleNotification` is `suspend`).
- `JournalRepository` serializes the read-modify-write with its own mutex —
  the same one the ViewModel path goes through — so UI edits and notifications
  can't interleave.
- `onListenerConnected` re-posts the status notification and replays
  `activeNotifications` (still in the shade) through the same worker, sorted by
  post time — dedup prevents double entries.
- Categorization (`NotificationHandler.matchCategory`) walks the user's rules
  in order, first regex match wins, fallback `expenses:unknown`.
- Raw notification body text is never logged or persisted (design §4.1); only
  the payee/amount end up in the journal.

## 3. Manual quick-add flow

```mermaid
sequenceDiagram
    participant U as User
    participant QA as QuickAddScreen
    participant VM as MainViewModel
    participant R as JournalRepository
    participant J as JournalWriter (pure)
    participant SAF as Journal file (SAF)

    U->>QA: amount, payee, account (pre-filled default)
    QA->>VM: addTransaction(tx)
    VM->>R: append(path, tx)
    R->>J: appendToContent(existing, tx)
    J-->>R: updated content + entries
    R->>SAF: backup + write content
    R-->>VM: JournalWriteResult
    VM-->>QA: emitMessage("Saved" / "Save failed — see Logs")
    VM->>VM: _allEntries updated
```

Manual and notification transactions produce the same `Transaction` type and
land in the journal identically — `JournalWriter` doesn't know which path
produced them. The ViewModel no longer touches `ContentResolver` directly:
`JournalRepository` owns all journal I/O.

## 4. UI state flow

```mermaid
flowchart LR
    subgraph sources["State sources"]
        entries["_allEntries<br/>(journal file content parsed<br/>by JournalWriter.readAll)"]
        sort["_sortOrder<br/>from DataStore"]
        limit["_pageLimit<br/>from DataStore"]
        filters["_filters<br/>from filters.yaml"]
        page["_page<br/>(transient, resets on<br/>sort/size/filter change)"]
    end

    combine["combine() in MainViewModel"]
    visible["EntryList.visible()<br/>1. FilterMatcher include/exclude<br/>2. sort<br/>3. paginate → EntryPage"]

    subgraph screen["MainScreen"]
        list["LazyColumn of TransactionRow"]
        pager["page footer (prev / Page X of Y / next)"]
        chips["FilterRow chips<br/>tap cycles INCLUDE → EXCLUDE → OFF"]
    end

    entries --> combine
    sort --> combine
    limit --> combine
    filters --> combine
    page --> combine
    combine --> visible --> list
    visible --> pager
    chips -->|cycleFilterMode| filters
    pager -->|nextPage / previousPage| page
```

`EntryList.visible` is pure: filter (include = any posting touches an included
account, exclude = no posting touches an excluded account), sort by
`SortOrder`, slice to one page, clamp out-of-range page numbers.

## 5. Journal edit / delete / bulk change

```mermaid
sequenceDiagram
    participant UI as MainScreen
    participant VM as MainViewModel
    participant R as JournalRepository
    participant J as JournalWriter (pure)
    participant SAF as Journal file (SAF)

    UI->>VM: editTransaction(offset, tx) / deleteTransaction(offset)
    VM->>R: replace(path, offset, tx) / delete(path, offset)
    R->>J: replaceInContent / deleteFromContent
    Note over J: uses lineOffset from the parsed JournalEntry
    R->>SAF: backup + write content
    R-->>VM: JournalWriteResult
    VM->>VM: _allEntries = result.entries<br/>(lineOffsets re-derived)
    VM-->>UI: message + refreshed list

    UI->>VM: changeTransactions(offsets, date, payee, currency, account)
    VM->>R: replaceMany(path, updates)
    R->>J: replaceManyInContent
    Note over J: applies offsets<br/>bottom-up so earlier offsets stay valid
    R->>SAF: backup + write content
```

Entries are addressed by `lineOffset` (line index in the journal), which is why
every write re-parses the whole file and returns fresh `JournalEntry` objects.

## 6. Data model

```mermaid
classDiagram
    class Transaction {
        +String date
        +String payee
        +List~Posting~ postings
        +String note
        +Source source
    }
    class Posting {
        +String account
        +String amount
        +String currency
    }
    class JournalEntry {
        +String date
        +String payee
        +List~Posting~ postings
        +int lineOffset
        +String note
    }
    class CategorizationRule {
        +String match
        +String account
    }
    class TransactionFilter {
        +String label
        +String account
        +FilterMode mode
    }
    class FilterMode {
        <<enumeration>>
        INCLUDE
        EXCLUDE
        OFF
    }
    class Source {
        <<enumeration>>
        Notification
        Manual
    }
    class SortOrder {
        <<enumeration>>
        NewestFirst
        OldestFirst
        HighestAmount
        LowestAmount
    }

    Transaction "1" --> "*" Posting
    JournalEntry "1" --> "*" Posting
    TransactionFilter --> FilterMode
    Transaction --> Source
```

`Transaction` is what gets written (empty posting amounts are auto-balanced by
`JournalWriter.format`); `JournalEntry` is what gets read back, carrying the
`lineOffset` needed for in-place edits. The `source` field is not persisted in
the journal — once written, entries are indistinguishable.

## 7. Screens and navigation

```mermaid
flowchart LR
    Main["Main<br/>transaction list, filters,<br/>sort, pagination"]
    QuickAdd["QuickAdd<br/>manual entry"]
    RawJournal["RawJournal<br/>read-only text view"]
    Rules["CategorizationRules<br/>payee regex → account"]
    Sources["NotificationSources<br/>allowlisted apps"]
    Logs["Logs<br/>in-app AppLogger feed"]
    Settings["Settings<br/>journal path, accounts, currency"]

    Main --> QuickAdd
    Main --> RawJournal
    Main --> Rules
    Main --> Sources
    Main --> Logs
    Main --> Settings
```

Routes are defined in `Screen.kt`; `MainViewModel` is the single shared
ViewModel across all destinations (`NotifLedgerNavGraph.kt`).

## 8. Where state lives

```mermaid
flowchart TB
    subgraph saf["User-chosen file (SAF content:// URI)"]
        journal["hledger journal — source of truth<br/>read + append + replace + delete"]
    end

    subgraph ds["DataStore Preferences (name: settings)"]
        keys["journal_path, default_account, default_currency,<br/>notification_sources, sort_order, page_limit,<br/>filter_row_limit, processed_notification_ids"]
    end

    subgraph internal["app filesDir (internal storage)"]
        rules["rules/categorization.yaml<br/>SnakeYAML, ordered rules"]
        filt["filters/filters.yaml<br/>SnakeYAML, label/account/mode"]
        logs["logs/notifledger.log<br/>AppLogger"]
        backup["journal_backup.txt<br/>pre-write recovery copy"]
    end
```

- Journal = the only source of truth for transactions (no Room, no cache).
- Rules are cached in `NotifLedgerApp` at process start for the listener path;
  the UI reloads them from disk.
- Dedup state lives in DataStore so listener reconnects don't duplicate
  entries.
