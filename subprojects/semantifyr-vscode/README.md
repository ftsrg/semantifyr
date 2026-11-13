<!--
  SPDX-FileCopyrightText: 2023-2026 The Semantifyr Authors

  SPDX-License-Identifier: EPL-2.0
-->

# Semantifyr for VS Code

Edit and verify OXSTS and Gamma models with [Semantifyr](https://semantifyr.org/), a framework to support the declarative definition of engineering model semantics. The extension runs Semantifyr's language servers in VS Code, lists the verification cases of your models in the Testing view, runs them on the backends you have installed, and opens the witness and the trace of a case beside the model.

## Features

- Editing OXSTS and Gamma: highlighting, diagnostics as you type, completion, hover, navigation and formatting.
- Verification: each case is a test in the Testing view; run one, a file or all of them, and read the verdict on the case.
- Witness and trace: where a verdict comes with a witness, **Show Witness** opens it beside the model, for a Gamma case as a Gamma execution trace, and the trace with the calls and the state changes of each step.
- Witness validation: a witness is verified again after each run, and **Validate Witness** repeats it.
- Gamma to OXSTS: **Semantifyr: Compile Gamma File** writes the OXSTS that a Gamma model compiles to.

## Requirements

- VS Code 1.109 or newer.
- Java 25 or newer.
- At least one backend. The extension's package holds none: Theta runs from `theta-xsts-cli` on `PATH` or in Docker, nuXmv, Spin with gcc, and Uppaal's `verifyta` from `PATH`. The default portfolio, `smart`, uses whichever are installed.

## SysML v2

This extension does not edit SysML v2, which has an extension of its own. Verify SysML v2 models in [Semantifyr Live](https://live.semantifyr.org) or with the `semantifyr-sysml` launcher.

## Settings

The `semantifyr.*` settings choose the portfolio, the timeout, the witness validation, how Theta is run, the artifacts and the optimization; the [website](https://semantifyr.org/) describes them.

## Status

Version 0.1.0 of a research prototype. Please report what you meet on the [issue tracker](https://github.com/ftsrg/semantifyr/issues).

## Learn more

- [Semantifyr website](https://semantifyr.org/)
- [Source code](https://github.com/ftsrg/semantifyr)

## License

Copyright (c) 2023-2026 [The Semantifyr Authors](https://github.com/ftsrg/semantifyr/blob/main/CONTRIBUTORS.md). Available under the [Eclipse Public License 2.0](https://www.eclipse.org/legal/epl-2.0/).
