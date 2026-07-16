# Coding Standards

## Core Principle

**Simple beats clever. Always.**

If a reviewer has to pause and think "wait, what does this do," it's wrong —
even if it works. Code is read far more often than it's written. Optimize for
the next person reading it, including future you.

## One Function, One Action, One Thing

- A function does exactly one thing. If you're using "and" to describe what
  it does, split it.
- A function should fit on one screen. If it doesn't, it's probably doing too
  much.
- Prefer many small, named functions over one large function with comments
  marking "sections."
- If a function has more than ~3 levels of nesting (if inside if inside
  loop), extract the inner logic into its own function.

```
// Bad
function processUser(user) {
  if (user.active) {
    if (user.email) {
      if (validateEmail(user.email)) {
        // send email, update db, log event, etc.
      }
    }
  }
}

// Good
function processUser(user) {
  if (!canEmailUser(user)) return;
  sendWelcomeEmail(user);
}

function canEmailUser(user) {
  return user.active && user.email && validateEmail(user.email);
}
```

## Stupid Simple, On Purpose

- Write the dumbest version that works first. Optimize only when you have a
  reason to (measured, not guessed).
- No cleverness for its own sake. If a one-liner needs a comment to explain
  what it does, write it as three plain lines instead.
- Avoid deep abstraction layers, factories, or indirection unless there are
  already 3+ concrete cases that need it. Don't build for a future that
  might not come.
- Prefer explicit over implicit. Magic (metaprogramming, hidden side
  effects, global mutable state) is a last resort, not a default.
- If you can delete code and nothing breaks, delete it.

## Naming

- Names say what a thing *is* or *does*, not how it's implemented.
- No abbreviations unless they're universally obvious (`id`, `url`, `i` in a
  tight loop). `usrCfg` is not faster to read than `userConfig`.
- Booleans read like yes/no questions: `isActive`, `hasPermission`,
  `canRetry`.
- Functions are verbs (`sendEmail`), values are nouns (`emailAddress`).

## Functions & Data

- Pure functions by default: same input, same output, no hidden state
  mutation. Side effects (I/O, network, db writes) go in clearly named,
  isolated functions — never buried inside "helper" logic.
- Avoid mutating arguments. Return new values instead.
- Prefer a few explicit parameters over a giant options object, unless
  there are genuinely optional settings (then an options object is fine).

## Comments

- Comments explain *why*, never *what*. If the *what* isn't obvious from
  the code, rewrite the code — don't caption it.
- Delete commented-out code. It's what git history is for.

## Errors

- Fail loudly and early. No silent catches that swallow errors.
- Handle errors at the boundary where you have enough context to actually
  do something about them — don't catch-and-rethrow for no reason.

## Structure

- File and folder structure mirrors what the code *does*, not the framework
  or pattern used to build it.
- No file should require scrolling to understand its purpose. If it does,
  split it.
- Group by feature, not by type (`user/` not `controllers/`, `models/`,
  `views/` scattered across the repo).

## Before Opening a PR

Ask yourself:
1. Could someone unfamiliar with this code understand it in one read?
2. Does every function do exactly one thing?
3. Is there anything here "just in case" that isn't used yet? Delete it.
4. Could this be simpler and still work?

If the answer to #4 is yes, it's not done yet.
