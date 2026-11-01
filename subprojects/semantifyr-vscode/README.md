<!--
  SPDX-FileCopyrightText: 2023-2026 The Semantifyr Authors

  SPDX-License-Identifier: EPL-2.0
-->

# Semantifyr for Visual Studio Code

Language support and verification tooling for the [Semantifyr](https://ftsrg.mit.bme.hu/semantifyr/) framework - a declarative approach to defining engineering model semantics for formal verification.

This extension brings the Semantifyr toolchain into VS Code, including a language server, syntax highlighting, and integrated commands for compiling and verifying models.

## Requirements

- Visual Studio Code `1.109.0` or newer.
- JVM 25 or newer.
- The used verification backends.

## Verification

The verification cases of the open workspace appear in the *Testing* view. When a verdict comes with a witness, **Show Witness** on the failure message or in the case's context menu opens the back-annotated witness (for a Gamma case, the witness as a Gamma execution trace) read-only beside the model, and next to it the trace with the calls and the state changes of each step.

## Extension settings

The `semantifyr.*` settings choose the portfolio, the timeout, the concurrency, the Theta executor and image, the artifacts and the optimization level. The default portfolio, `smart-full`, races every installed backend, so one of Theta, nuXmv, Spin or Uppaal is enough.

## Known issues

This extension is in early development. Please report any issues you encounter on the [issue tracker](https://github.com/ftsrg/semantifyr/issues).

## Learn more

- [Semantifyr website](https://ftsrg.mit.bme.hu/semantifyr/)
- [Language reference](https://ftsrg.mit.bme.hu/semantifyr/learn/language/)
- [Tutorials](https://ftsrg.mit.bme.hu/semantifyr/learn/tutorials/)
- [Source code on GitHub](https://github.com/ftsrg/semantifyr)

## License

Copyright (c) 2023-2026 [The Semantifyr Authors](https://github.com/ftsrg/semantifyr/blob/main/CONTRIBUTORS.md)

Semantifyr is available under the [Eclipse Public License - v 2.0](https://www.eclipse.org/legal/epl-2.0/).
