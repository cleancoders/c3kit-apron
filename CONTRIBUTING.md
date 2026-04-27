# Contributing to Apron

Thanks for your interest! Apron is part of the [c3kit](https://github.com/cleancoders/c3kit) family of libraries.

## Getting Started

1. Fork and clone the repo.
2. Make sure you have a JDK 21+ and the [Clojure CLI](https://clojure.org/guides/install_clojure) installed.
3. Run the test suite to confirm a green baseline:

```sh
clj -M:test:spec        # JVM specs (one-shot)
clj -M:test:spec -a     # JVM specs (auto-runner)
clj -M:test:cljs once   # CLJS specs (one-shot, runs in headless browser via Playwright)
```

## Workflow

- Create a feature branch off `master`.
- **Use TDD.** Write a failing spec first, then the minimum code to make it pass, then refactor.
- Keep commits small and focused. Write descriptive commit messages.
- Update `CHANGES.md` with a one-line entry under a new or current version section.
- If you change the public API, update `SCHEMA.md` and any relevant docstrings.

## Code Style

- Idiomatic Clojure: prefer `->` / `->>` threading, keep functions small and focused.
- `!`-suffix for fns that throw; `->type` / `<-type` symmetry for converters.
- Don't column-align values in maps; use single spaces.

## Submitting a PR

1. Ensure JVM and CLJS specs pass.
2. Open a PR against `master`.
3. Describe what changed and why; link any related issues.

## Reporting Bugs / Requesting Features

Open an issue. Include:
- Apron version and Clojure version
- A minimal reproduction
- Expected vs actual behavior
