---
title: Develop
sidebar_position: 0
---

# Developing Semantifyr

Semantifyr is open source. Contributions are welcome, whether that's a bug fix, a new frontend, a documentation improvement, or a discussion about an architectural change.

If you only want to *use* Semantifyr to verify models, the [Learn](../learn/index.md) section is a better starting point.

## Getting in touch

- [GitHub Issues](https://github.com/ftsrg/semantifyr/issues) for bug reports and feature requests.
- [GitHub Discussions](https://github.com/ftsrg/semantifyr/discussions) for questions and design proposals.
- The maintainers are listed in [`CONTRIBUTORS.md`](https://github.com/ftsrg/semantifyr/blob/main/CONTRIBUTORS.md).

For anything beyond a small fix, please open an issue or discussion first. A short conversation about scope and approach saves time during review.

## Fork and clone

We recommend forking the repository to your own GitHub account, then cloning your fork:

```bash
git clone https://github.com/<your-username>/semantifyr.git
cd semantifyr
```

This gives you a place to push branches and open pull requests from.

## Building from source

**Prerequisites:** JDK 25 (the Gradle toolchain downloads it automatically) and Git. No separate Gradle or Node.js install is needed; the build manages both.

```bash
./gradlew build
```

The first build downloads the JDK toolchain, a project-local Node distribution, the Theta CLI, and Maven dependencies. Subsequent builds use the Gradle cache and are much faster.

This runs the unit tests and the verification smoke tests, and assembles every artifact. Tests that need a backend binary skip when it is missing; the build provides Theta. The broader suites run separately:

```bash
./gradlew integrationTest   # the semantic conformance and frontend corpus suites
./gradlew nightlyTest       # long-running cases and witness re-checks on all backends
```

Pass `-Ptests.requiredBackends=theta,spin` to fail instead of skip when a listed backend is missing.

## Where to start

The repository is organized under `subprojects/`:

| Area | Path | Description |
|------|------|-------------|
| Language | `subprojects/oxsts.lang/` | Grammar, validator, scoping, type system |
| Language server | `subprojects/oxsts.lang.ide/` | OXSTS language server |
| Compiler | `subprojects/compiler/` | Instantiator, inliner, flattener, optimizer |
| Verifier | `subprojects/verifier/` | Verification cases, verdicts, witnesses |
| Backends | `subprojects/backends/` | Theta, nuXmv, Spin and Uppaal integrations |
| Portfolios | `subprojects/portfolios/` | Backend configurations raced or combined per case |
| Frontends | `subprojects/frontends/` | Gamma and SysML v2 frontends |
| CLI | `subprojects/semantifyr-cli/` | The `semantifyr` command-line interface |
| VS Code extension | `subprojects/semantifyr-vscode/` | Extension packaging and launch configs |
| Live editor | `subprojects/semantifyr-live/` | The browser-based editor frontend and backend |
| Website | `subprojects/website/` | This documentation site |

## Contributing a change

1. Create a branch on your fork.
2. Make your changes. Run `./gradlew build` to verify.
3. Ensure all files have [REUSE-compliant](https://reuse.software/) SPDX license headers.
4. Open a pull request against `main`. Describe what changed and why.

By contributing you agree that your contribution is licensed under the project's [Eclipse Public License v2.0](https://github.com/ftsrg/semantifyr/blob/main/LICENSE).
