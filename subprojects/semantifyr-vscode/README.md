<!--
  SPDX-FileCopyrightText: 2023-2026 The Semantifyr Authors

  SPDX-License-Identifier: EPL-2.0
-->

# Semantifyr for VS Code

Edit and verify Semantifyr and Gamma models with [Semantifyr](https://semantifyr.org/), a framework to declaratively define the semantics of engineering modeling languages.

## Features

- Editing the Semantifyr Language and Gamma: highlighting, diagnostics, completion, hover, navigation, and formatting.
- Verification: each case is a test in the Testing view.
- Witness and trace: **Show Witness** opens the witness of a verdict beside the model, and **Validate Witness** verifies it again.
- **Semantifyr: Compile Gamma File** writes the Semantifyr model that a Gamma model compiles to.

## Requirements

- VS Code 1.109 or newer.
- Java 25 or newer.
- At least one backend on `PATH`: Theta (`theta-xsts-cli`, or Docker), nuXmv, Spin with gcc, or Uppaal's `verifyta`.

## License

Copyright (c) 2023-2026 [The Semantifyr Authors](https://github.com/ftsrg/semantifyr/blob/main/CONTRIBUTORS.md). Available under the [Eclipse Public License - v 2.0](https://www.eclipse.org/legal/epl-2.0/).
