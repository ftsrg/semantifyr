---
title: Contributing
sidebar_position: 1
description: How to get in touch, set up a clone and propose a change to Semantifyr.
---

# Contributing

Semantifyr is open source, and contributions are welcome: a bug fix, a new frontend, a correction of these pages, or a discussion of an architectural change. If you only want to *use* Semantifyr, [Learn](../learn/index.mdx) is the place to start.

## Getting in Touch

- [GitHub Issues](https://github.com/ftsrg/semantifyr/issues) for bug reports and feature requests.
- [GitHub Discussions](https://github.com/ftsrg/semantifyr/discussions) for questions and design proposals.
- The maintainers are listed in [`CONTRIBUTORS.md`](https://github.com/ftsrg/semantifyr/blob/main/CONTRIBUTORS.md).

For anything beyond a small fix, open an issue or a discussion first: a short conversation about scope and approach saves time in review.

## Setting Up

Fork the repository to your own GitHub account and clone your fork, which gives you a place to push branches and open pull requests from. The [README](https://github.com/ftsrg/semantifyr#build-from-source) says how to build and test it; [`CONTRIBUTING.md`](https://github.com/ftsrg/semantifyr/blob/main/CONTRIBUTING.md) holds the rules a pull request follows. The [architecture](index.md) is the map of the code.

## Making a Change

1. Create a branch on your fork.
2. Make your change, and change the pages of this site that describe what you changed in the same pull request. Run `./gradlew build`.
3. Give every new file its [REUSE](https://reuse.software/) SPDX header, copied from a neighbouring file of the same type.
4. Open a pull request against `main`, saying what changed and why.

By contributing you agree that your contribution is licensed under the project's [Eclipse Public License v2.0](https://github.com/ftsrg/semantifyr/blob/main/LICENSE).
