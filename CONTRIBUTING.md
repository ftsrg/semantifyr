<!--
  SPDX-FileCopyrightText: 2026 The Semantifyr Authors

  SPDX-License-Identifier: EPL-2.0
-->

# Contributing to Semantifyr

## Quick reference

- **Have a question or proposal?** Open a [GitHub Discussion](https://github.com/ftsrg/semantifyr/discussions) or an [issue](https://github.com/ftsrg/semantifyr/issues). For larger changes, please discuss the design before you start coding.
- **Found a bug?** Open an [issue](https://github.com/ftsrg/semantifyr/issues) with a minimal reproduction.
- **Want to make a code change?** Fork, branch from `main`, and open a pull request against `main`. Run `./gradlew build` locally before pushing.
- **Changing what users see?** Update the website pages under `subprojects/website/src/docs` in the same pull request. The command-line help and the portfolio table on the website are generated from the CLI at build time.

## Licensing

By contributing you agree that your contribution is licensed under the [Eclipse Public License v2.0](LICENSE), the same license as the rest of the codebase. Every source file must carry a valid SPDX header in the [REUSE](https://reuse.software/) format; copy it from a neighbouring file of the same type. The CI `reuse-check` job will fail your pull request if a new file is missing one.

## Code of conduct

This project follows a [Code of Conduct](CODE_OF_CONDUCT.md) adapted from the Contributor Covenant. By participating you agree to abide by its terms.

## Maintainers

Project leadership and contact information is listed in [CONTRIBUTORS.md](CONTRIBUTORS.md).
