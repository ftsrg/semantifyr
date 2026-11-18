---
title: Contributing
sidebar_position: 1
description: The essentials of contributing to Semantifyr - getting in touch, building it from a clone, the test suites and what to run for a change, the checks a review relies on, and making the change.
---

# Contributing

## Getting in Touch

- [GitHub Issues](https://github.com/ftsrg/semantifyr/issues) for bug reports, with a minimal model that shows the bug, and for feature requests.
- [GitHub Discussions](https://github.com/ftsrg/semantifyr/discussions) for questions and design proposals.
- The maintainers are listed in [`CONTRIBUTORS.md`](https://github.com/ftsrg/semantifyr/blob/main/CONTRIBUTORS.md).

For anything beyond a small fix, open an issue or a discussion before you start: scope and approach are cheaper to settle before the code than in review.

## Building and Running From a Clone

Fork the repository and clone your fork. [Install](../learn/start/install.md) says what a build needs and builds the launchers and the VS Code extension from the clone; Gradle downloads the rest, Theta and Node.js among it, and a test that needs nuXmv, Spin or Uppaal is skipped when that backend is not on `PATH`. [Architecture](index.md) says where each part of Semantifyr lives and where to start reading it.

## Test Suites

A test lives in the suite of what it proves, each suite a Gradle task of the modules that have such tests:

- `test`: unit tests, one unit alone, with fakes in place of the backends. `build` runs it.
- `adapterTest`: an adapter and its real backend understand each other, each backend skipped when it is missing. `build` runs it.
- `semanticTest`: a construct of the language or a class of a semantic library means what it should, through the compiler and a backend.
- `integrationTest`: our parts working together, the frontends' corpora and the served examples through to a verdict, and live.
- `uiTest`: the built frontend in a browser against a mock backend, live's screens and its built application, and the website's walk by keyboard, in the browser `-PtestBrowser` names, `chromium` (the default), `firefox` or `webkit`; the walk runs in Chromium alone.
- `endToEndTest`: a package through its real entry point, the launchers' distributions, the VS Code extension and the server image.

`build` compiles, runs the static checks and the `test` and `adapterTest` suites, and packs nothing; a package has its own task, such as `installDist`. `./gradlew assemble` compiles every source set and builds every package. `./gradlew continuousIntegration` runs the semantic, integration, end-to-end and UI suites, which CI runs beside the build, and `./gradlew nightly` what the nightly runs on a machine with all four backends, the cases a manifest marks as nightly included. The TypeScript packages run their unit tests in their `npmCheck` task, beside the type check and the linter.

Run the suite that holds what you changed, as a task of the module you changed: `./gradlew :website:test` runs the website's unit tests, which validate every code example of these pages in the Semantifyr Language, and `./gradlew :website:npmCheck` its type check, linter and Vitest suites. A change of these pages also needs `./gradlew :website:assemble`, which fails on a broken link, and a change of their controls `./gradlew :website:uiTest`. On a pull request CI compiles and packs once with `assemble` on Linux, and the jobs after it start from what it made: `build` on Linux, and with Theta, Spin and nuXmv the semantic, integration and end-to-end suites each in a job of its own and the UI suite once per browser; `build` on Windows and macOS starts with the run and takes nothing from it. It takes at most 30 minutes, and a failed job or a failed SonarCloud quality gate stops the merge.

## What a Review Checks

- The checks of CI pass: formatting, which `./gradlew spotlessCheck` reports (ktlint for Kotlin, palantir-java-format for Java), the TypeScript type check and linter, the tests, SonarCloud's quality gate and the REUSE check.
- Every new file carries its SPDX header in the [REUSE](https://reuse.software/) format, copied from a neighbouring file of the same type.
- A change that alters what a user sees changes the pages of this site in the same pull request. The command-line help, the portfolio table, the construct tables, the standard library and the VS Code settings on these pages are generated from the code, and the code examples are validated by the website's tests.
- The project's words are used: the website's tests fail on a page or a text of live that names a thing by a word the project has rejected for it.

## Making a Change

1. Create a branch on your fork.
2. Make the change, with its tests at the level that proves it, and the pages it alters.
3. Run `./gradlew build` before you push, as the repository's [`CONTRIBUTING.md`](https://github.com/ftsrg/semantifyr/blob/main/CONTRIBUTING.md) asks; it runs the formatting check and the `test` and `adapterTest` suites with the rest of `check`.
4. Open a pull request against `main`, saying what changed and why.

By contributing you agree that your contribution is licensed under the project's [Eclipse Public License v2.0](https://github.com/ftsrg/semantifyr/blob/main/LICENSE).
