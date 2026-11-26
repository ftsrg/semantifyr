<!--
  SPDX-FileCopyrightText: 2026 The Semantifyr Authors

  SPDX-License-Identifier: EPL-2.0
-->

# Contributing to Semantifyr

Ask questions and propose designs in [Discussions](https://github.com/ftsrg/semantifyr/discussions), and report bugs with a minimal model in [Issues](https://github.com/ftsrg/semantifyr/issues).

For information on the architecture, see the [architecture page](https://semantifyr.org/develop/).

## Building from source

Requirements:
- Java 25

`./gradlew build` builds the whole project.

Gradle downloads Theta and Node.js automatically. A test that needs nuXmv, Spin, or Uppaal is skipped when that backend is not available.

## Pull requests

Fork, branch from `main`, and open a pull request against `main`.

## Licensing

Contributions are licensed under the [Eclipse Public License 2.0](LICENSE). Every new file carries an SPDX header in the [REUSE](https://reuse.software/) format.

## Code of conduct

This project follows the [Code of Conduct](CODE_OF_CONDUCT.md). The maintainers are listed in [CONTRIBUTORS.md](CONTRIBUTORS.md).
