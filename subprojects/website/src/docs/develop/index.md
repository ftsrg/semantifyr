---
title: Contributing
sidebar_position: 0
description: How to build Semantifyr from a clone and contribute a change.
---

# Contributing to Semantifyr

You can clone the Semantifyr repository from GitHub at https://github.com/ftsrg/semantifyr. If you want to contribute code, we recommend [forking](https://docs.github.com/en/pull-requests/collaborating-with-pull-requests/working-with-forks/fork-a-repo) the repository, so that you can open a [pull request](https://github.com/ftsrg/semantifyr/pulls) later. For anything beyond a small fix, open an [issue](https://github.com/ftsrg/semantifyr/issues) or a [discussion](https://github.com/ftsrg/semantifyr/discussions) first, so that we can agree on the approach before you write the code.

## Required Tools

Semantifyr is written in Kotlin, Java, and TypeScript. To build it, you need a Java 25 compatible JDK. Gradle downloads everything else, including Node.js and the Theta backend. The tests that need nuXmv, Spin, or Uppaal are skipped when that backend is not on your `PATH`.

## Building Semantifyr {#building}

To build Semantifyr, run

```bash
./gradlew build
```

in the cloned repository. This compiles all code, runs the static checks and the unit tests, and should complete without errors. Run it before you open a pull request. CI runs it too, together with the longer test suites.

To build the CLIs and the VS Code extension, run

```bash
./gradlew pack
```

It builds the CLIs in [`semantifyr-cli`](https://github.com/ftsrg/semantifyr/tree/main/subprojects/semantifyr-cli), [`sysml-cli`](https://github.com/ftsrg/semantifyr/tree/main/subprojects/frontends/sysml/sysml-cli), and [`gamma-cli`](https://github.com/ftsrg/semantifyr/tree/main/subprojects/frontends/gamma/gamma-cli), and the extension in [`semantifyr-vscode`](https://github.com/ftsrg/semantifyr/tree/main/subprojects/semantifyr-vscode).

The maintainers are listed in [`CONTRIBUTORS.md`](https://github.com/ftsrg/semantifyr/blob/main/CONTRIBUTORS.md).

By contributing, you agree that your contribution is licensed under the project's [Eclipse Public License v2.0](https://github.com/ftsrg/semantifyr/blob/main/LICENSE).
