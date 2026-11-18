---
title: Contributing
sidebar_position: 1
description: The essentials of contributing to Semantifyr - getting in touch, building it from a clone, the test levels and what to run for a change, the checks a review relies on, and making the change.
---

# Contributing

## Getting in Touch

- [GitHub Issues](https://github.com/ftsrg/semantifyr/issues) for bug reports, with a minimal model that shows the bug, and for feature requests.
- [GitHub Discussions](https://github.com/ftsrg/semantifyr/discussions) for questions and design proposals.
- The maintainers are listed in [`CONTRIBUTORS.md`](https://github.com/ftsrg/semantifyr/blob/main/CONTRIBUTORS.md).

For anything beyond a small fix, open an issue or a discussion before you start: scope and approach are cheaper to settle before the code than in review.

## Building and Running From a Clone

Fork the repository and clone your fork. [Install](../learn/start/install.md) says what a build needs and builds the launchers and the VS Code extension from the clone; Gradle downloads the rest, Theta and Node.js among it, and a test that needs nuXmv, Spin or Uppaal is skipped when that backend is not on `PATH`. [Architecture](index.md) says where each part of Semantifyr lives and where to start reading it.

## Test Levels

Every JVM module has the test suite `test`, and a module whose tests run real backends adds some of three more, each a Gradle task:

- `test`: unit tests, with fakes in place of the backends. `check` runs it.
- `verificationTest`: smoke tests that run a real backend, one case per backend, each backend skipped when it is missing. `check` runs it.
- `integrationTest`: the system end to end over the semantic test models and the frontends' corpora, on the real backends. `check` does not run it; CI does, after the build.
- `nightlyTest`: the slow models and the witnesses checked by every backend. It runs nightly, on a machine with all four backends.

The TypeScript packages run their Vitest suites in their `npmCheck` task, beside the type check and the linter, which `check` runs too. Live's frontend adds `screenTest` and `uiTest`, which drive its screens and its built application in a browser, and the website `walkTest`, a walk by keyboard through the built site.

Run the level that holds what you changed, as a task of the module you changed: `./gradlew :website:test` runs the website's unit tests, which validate every code example of these pages in the Semantifyr Language, and `./gradlew :website:npmCheck` its type check, linter and Vitest suites. A change of these pages also needs `./gradlew :website:assemble`, which fails on a broken link, and a change of their controls `./gradlew :website:walkTest`. CI runs `./gradlew build` on Linux, Windows and macOS, and on Linux the browser levels and `integrationTest` as well.

## What a Review Checks

- The checks of CI pass: formatting, which `./gradlew spotlessCheck` reports (ktlint for Kotlin, palantir-java-format for Java), the TypeScript type check and linter, the tests, and the REUSE check.
- Every new file carries its SPDX header in the [REUSE](https://reuse.software/) format, copied from a neighbouring file of the same type.
- A change that alters what a user sees changes the pages of this site in the same pull request. The command-line help, the portfolio table, the construct tables, the standard library and the VS Code settings on these pages are generated from the code, and the code examples are validated by the website's tests.
- The project's words are used: the website's tests fail on a page or a text of live that names a thing by a word the project has rejected for it.

## Making a Change

1. Create a branch on your fork.
2. Make the change, with its tests at the level that proves it, and the pages it alters.
3. Run `./gradlew build` before you push, as the repository's [`CONTRIBUTING.md`](https://github.com/ftsrg/semantifyr/blob/main/CONTRIBUTING.md) asks; it runs the formatting check and the `test` and `verificationTest` suites with the rest of `check`.
4. Open a pull request against `main`, saying what changed and why.

By contributing you agree that your contribution is licensed under the project's [Eclipse Public License v2.0](https://github.com/ftsrg/semantifyr/blob/main/LICENSE).
