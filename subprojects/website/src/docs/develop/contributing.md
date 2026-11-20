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
- `integrationTest`: our parts working together, the served examples through to a verdict, and live.
- `uiTest`: the built frontend in a browser against a mock backend, live's screens and its built application, and the website's walk by keyboard, in the browser `-PtestBrowser` names, `chromium` (the default), `firefox` or `webkit`; the walk runs in Chromium alone.
- `endToEndTest`: a package through its real entry point, the launchers' distributions, the VS Code extension and the server image.

`build` compiles, runs the static checks and the `test` and `adapterTest` suites, and of the packages makes the application modules' distribution archives alone; every other package has its own task, such as `installDist`. `./gradlew pack` builds every package, and a module's `pack` its own, such as `:website:pack`. `./gradlew continuousIntegration` runs the semantic, integration, end-to-end and UI suites, and `./gradlew nightlyTest` the nightly suite, the cases a manifest marks as nightly, on a machine with all four backends. The TypeScript packages run their unit tests in their `npmCheck` task, beside the type check and the linter.

The frontend tests are not a Gradle suite: they check that the models of the Gamma and SysML v2 corpora and their semantic libraries reach the verdicts their `manifest.json` expects, through an installed launcher. The `corpus-check` harness runs the launcher once per model file with `--json` and fails on a case that misses its verdict, times out or has no answer; it leaves out the cases a manifest marks as nightly. To run them locally, install the harness, the launchers and the SysML v2 corpus with its further cases, then run the harness with one launcher at a time:

```sh
./gradlew :corpus-check:installDist :gamma-cli:installDist :sysml-cli:installDist :sysml-wrapper:appendFurtherCases
subprojects/corpus-check/build/install/corpus-check/bin/corpus-check \
  --launcher subprojects/frontends/gamma/gamma-cli/build/install/semantifyr-gamma/bin/semantifyr-gamma \
  --corpus subprojects/frontends/gamma/models/semantics \
  --timeout-seconds 120
subprojects/corpus-check/build/install/corpus-check/bin/corpus-check \
  --launcher subprojects/frontends/gamma/gamma-cli/build/install/semantifyr-gamma/bin/semantifyr-gamma \
  --corpus subprojects/frontends/gamma/models/examples \
  --timeout-seconds 1800
subprojects/corpus-check/build/install/corpus-check/bin/corpus-check \
  --launcher subprojects/frontends/sysml/sysml-cli/build/install/semantifyr-sysml/bin/semantifyr-sysml \
  --corpus subprojects/frontends/sysml/sysml-wrapper/build/corpus --case-namespace semantifyr::sysml::model:: \
  --timeout-seconds 120
```

Run the suite that holds what you changed, as a task of the module you changed: `./gradlew :website:test` runs the website's unit tests, which validate every code example of these pages in the Semantifyr Language, and `./gradlew :website:npmCheck` its type check, linter and Vitest suites. A change of these pages also needs `./gradlew :website:pack`, which fails on a broken link, and a change of their controls `./gradlew :website:uiTest`. On a pull request CI runs `build` and the semantic, integration and end-to-end suites on Linux, Windows and macOS, with Spin and nuXmv on Linux alone, the UI suite once per browser, and, after the build, the frontend tests on Linux, one job per frontend, on the launchers and the harness the Linux build uploads; on Linux it also publishes to SonarCloud and packs with `pack`. A failed job or a failed SonarCloud quality gate stops the merge.

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
