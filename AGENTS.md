# NotifLedger — Agent Notes

## Development Environment

**Always use `devenv`** to run any commands. The Android SDK, Java 17, and Gradle
are provided by the Nix-based development environment.

```sh
# Run tests
devenv shell ./gradlew test

# Build APK
devenv shell ./gradlew assembleDebug

# Install on device
devenv shell ./gradlew installDebug
```

## Project Structure

- **Single-module** Android app (no multi-module Clean Architecture)
- **No DI framework** — manual construction via `Application` class
- **No Room database** — file I/O through `ContentResolver` (SAF URIs)
- **Package**: `org.notifledger.app`
- **SDK**: compileSdk/targetSdk 34, minSdk 26

## Key Architecture Decisions

- `JournalWriter` is a pure text processor — no file I/O, works with strings
- `ParserEngine` is a pure function — no side effects, takes text + rule, returns Transaction
- `RuleIO` handles YAML persistence of parser and categorization rules
- `MainViewModel` (AndroidViewModel) is the single ViewModel, used by all screens
- `SettingsManager` wraps DataStore for preferences
- `NotifListener` (NotificationListenerService) captures notifications

## Coding Standards

See `docs/coding_standarts.md` — the core principle is **Simple beats clever. Always.**

## Tests

- 19 unit tests in `app/src/test/java/`
- JUnit 4, no mocking framework
- Tests cover: `JournalWriter`, `ParserEngine`, `RuleIO`