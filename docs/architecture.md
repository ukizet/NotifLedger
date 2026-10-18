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
        application["NotifLedgerApp<br/>Application: settings, rules cache,<br/>shared journalWriteMutex"]
        listener["NotifListener<br/>NotificationListenerService"]
        handler["NotificationHandler<br/>allowlist check + categorization"]
        parser["ParserEngine<br/>first number = amount, title = payee"]
        helper["NotificationHelper<br/>persistent listening status notification"]
        dedup["NotificationDedup<br/>processed notification ids"]
        activity["MainActivity<br/>Compose + NavHost"]
        vm["MainViewModel<br/>all UI state, SAF journal I/O"]
        writer["JournalWriter<br/>pure text: format / readAll / replace / delete"]
    end

    subgraph storage["Storage"]
        journal["hledger journal file<br/>content:// URI via SAF"]
        datastore["DataStore Preferences<br/>journal path, accounts, sources, sort, page size"]
        files["App-internal filesDir<br/>rules/categorization.yaml<br/>filters/filters.yaml<br/>logs/notifledger.log"]
    end

    bank --> framework --> listener
    listener --> handler --> parser
    listener --> dedup
    handler --> writer
    listener -->|read + append via ContentResolver| journal
    listener -->|runBlocking reads| application
    application --> datastore
    application -->|loads rules at startup| files

    activity --> vm --> writer
    vm -->|read + write via ContentResolver| journal
    vm --> datastore
    vm -->|rules + filters YAML| files
    listener -.->|posts status notification| helper
```

No DI framework and no database: `NotifLedgerApp` constructs `SettingsManager`,
the notification path reads settings from it synchronously (`runBlocking`), and
the only durable stores are the journal file, DataStore, and a few YAML/log
files.

## 2. Notification capture flow (automatic path)

```mermaid
sequenceDiagram
    participant Bank as Bank app
    participant System as Android framework
    participant L as NotifListener
    participant D as NotificationDedup
    participant H as NotificationHandler
    participant P as ParserEngine
    participant J as JournalWriter
    participant SAF as Journal file (SAF)
    participant DS as DataStore

    Bank->>System: posts notification
    System->>L: onNotificationPosted(sbn)

    L->>L: shouldProcess(package in allowed sources)?
    Note over L: not allowlisted → return, nothing recorded

    L->>L: journal URI set?
    Note over L: no path configured → warn + return

    L->>DS: processedNotificationIds (runBlocking)
    L->>D: add(postTime|key)
    alt id already seen
        D-->>L: null → return (no duplicate entry)
    else new id
        D-->>L: updated raw ids (kept, max 200)
    end

    L->>L: extract android.title / android.text
    L->>P: parse(title, text, category account, default account, currency)
    alt number found
        P-->>L: Transaction(amount, payee = title)
    else no number
        P-->>L: null
        L->>H: createBestEffortTransaction(...)
        Note over H: empty amounts, still posted — never dropped
    end

    L->>SAF: read existing content (ContentResolver)
    L->>J: appendToContent(existing, tx)
    J-->>L: updated content + parsed entries
    L->>SAF: write content ("wt")
    L->>DS: persist updated dedup ids
```

Notes:

- The whole read-modify-write runs under `NotifLedgerApp.journalWriteMutex`,
  the same mutex `MainViewModel` uses, so UI edits and notifications can't
  interleave.
- `onListenerConnected` re-posts the status notification and replays
  `activeNotifications` (still in the shade) through the same path, sorted by
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
    participant M as journalWriteMutex
    participant J as JournalWriter
    participant SAF as Journal file (SAF)

    U->>QA: amount, payee, account (pre-filled default)
    QA->>VM: addTransaction(tx)
    VM->>M: withLock
    VM->>SAF: read existing content
    VM->>J: appendToContent(existing, tx)
    J-->>VM: updated content + entries
    VM->>SAF: write content
    VM-->>QA: emitMessage("Saved" / "Save failed — see Logs")
    VM->>VM: _allEntries updated
```

Manual and notification transactions produce the same `Transaction` type and
land in the journal identically — `JournalWriter` doesn't know which path
produced them.

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
    participant M as journalWriteMutex
    participant SAF as Journal file (SAF)

    UI->>VM: editTransaction(offset, tx) / deleteTransaction(offset)
    VM->>M: withLock
    VM->>SAF: read current content
    Note over VM: JournalWriter.replaceInContent / deleteFromContent<br/>uses lineOffset from the parsed JournalEntry
    VM->>SAF: write content
    VM->>VM: _allEntries = result.entries<br/>(lineOffsets re-derived)
    VM-->>UI: message + refreshed list

    UI->>VM: changeTransactions(offsets, date, payee, currency, account)
    VM->>M: withLock
    Note over VM: replaceManyInContent applies offsets<br/>bottom-up so earlier offsets stay valid
    VM->>SAF: write content
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

    Transaction "1" --> "2..*" Posting
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
    end
```

- Journal = the only source of truth for transactions (no Room, no cache).
- Rules are cached in `NotifLedgerApp` at process start for the listener path;
  the UI reloads them from disk.
- Dedup state lives in DataStore so listener reconnects don't duplicate
  entries.
