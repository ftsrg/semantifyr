---
title: Getting Started
sidebar_position: 1
---

# Getting Started

New to Semantifyr? Start with the [Tutorials](tutorials/index.md): the first ones run in the browser with nothing to install.

There are three ways to start using Semantifyr today, and a fourth is planned. Pick whichever fits your situation. All of them give you the same language and the same compiler; the verification backends available depend on the environment.

## Option A: Try in the browser (no installation)

The fastest way to try Semantifyr. The **in-browser live editor** is a lightweight Monaco-based editor connected to the Semantifyr language server. It supports syntax highlighting, diagnostics, content assist, inlay hints for parameter names, loop variable types, default multiplicities and computed constants, and the verification cases of the model, which **Verify all** or a case's own run button verifies, each with its verdict and its trace. Its SysML v2 language edits one SysML v2 file with the standard and verification libraries at hand; the language server runs in your browser, and a failing case shows its trace as a SysML v2 model.

[Open the live editor](https://live.semantifyr.org)

Sessions are ephemeral. **Save** in the toolbar writes your model to a file, and **Share** puts the model and its results into a link.

## Option B: Use the JupyterHub environment (full IDE, no installation)

:::info[Planned]
A hosted JupyterHub environment that runs VS Code in the browser with the Semantifyr extension, the Theta backend, and example models for SysML v2 and Gamma. It is offline until it is rebuilt; until then, use the live editor or a local install.
:::

## Option C: Install the VS Code extension (for local work)

The Semantifyr VS Code extension bundles the language servers and the compiler. Verification runs on the backends installed on your machine. Use it when you want to work on your own machine, keep models in your own repository, or integrate Semantifyr into a longer-lived project.

**Requirements**

- [Visual Studio Code](https://code.visualstudio.com/) 1.109 or newer
- A Java 25 runtime, found through `JAVA_HOME` or `PATH` (the extension uses it to run the bundled language servers)
- At least one verification backend: Theta (`theta-xsts-cli` on `PATH`, or Docker), Spin (`spin` and `gcc` on `PATH`), nuXmv (`nuXmv` on `PATH`) or Uppaal (`verifyta` on `PATH`)

**Install**

1. Build the extension from a clone of the [repository](https://github.com/ftsrg/semantifyr) with `./gradlew :semantifyr-vscode:bundleExtension`, which writes `subprojects/semantifyr-vscode/build/semantifyr-<version>.vsix`. The build needs Git and a Java 25 runtime and downloads everything else. Once a release is published, you can download the `.vsix` from the [latest release](https://github.com/ftsrg/semantifyr/releases/latest) instead.
2. In VS Code, run **Extensions: Install from VSIX...** from the Command Palette (`Ctrl+Shift+P` / `Cmd+Shift+P`) and select the `.vsix` file.
3. Open any folder containing a `.oxsts` or `.gamma` file (`.gamma` is the language of the in-tree Gamma frontend, a sub-language of Gamma in Gamma's syntax). The extension activates automatically and provides syntax highlighting, diagnostics, and the verification cases in the *Testing* view. The cases follow unsaved edits, and the generated models under `.artifacts/` are not listed. **Show Type Hierarchy** on an OXSTS class lists its superclasses up to the builtin `Anything` and its subclasses in the workspace; on a transition, property or feature it lists the member it redefines and the members that redefine it.
4. Run a case from the *Testing* view. When the verdict comes with a witness (the counterexample of a violated invariant, or the path to a reachable state), **Show Witness** on the failure message or in the case's context menu opens the back-annotated witness (for a Gamma case, the witness as a Gamma execution trace) read-only beside the model, and next to it the trace: the calls and the state changes of each step. After a verdict with a witness the extension validates the witness, verifying it again (`semantifyr.verification.autoValidate`, on by default, on `semantifyr.verification.validationPortfolio`, by default the verification portfolio), and adds the result to the test message and the trace; **Validate Witness** in the same places runs it again.
5. **Inline** above an OXSTS case compiles it and opens the inlined model read-only beside the source. **Inline (with steps)** also writes the model after each compiler pass to `pipeline/steps/` next to it, in the artifacts directory `semantifyr.artifacts.location` selects. **Compile** above an opened inlined model compiles it, unsaved edits included, and opens the result, `compiled.oxsts`, the same way.

:::info[Planned]
Publication on the Visual Studio Marketplace is planned.
:::

## Option D: Install the command-line interface (for scripts and CI)

The `semantifyr` command lists, compiles and verifies the verification cases of OXSTS models from a terminal; `semantifyr-sysml` verifies SysML v2 models and `semantifyr-gamma` Gamma models. Each launcher has a distribution of its own with the Theta backend, its semantic library and a smoke model. They need a Java 25 runtime.

1. Build the distribution from a clone of the [repository](https://github.com/ftsrg/semantifyr) with `./gradlew :semantifyr-cli:installDist`, which installs it to `subprojects/semantifyr-cli/build/install/semantifyr`; `./gradlew :semantifyr-cli:distZip` packs the same files into `subprojects/semantifyr-cli/build/distributions/semantifyr-<version>.zip`. `:sysml-cli` and `:gamma-cli` build `semantifyr-sysml` and `semantifyr-gamma` the same way, under `subprojects/frontends/sysml/sysml-cli/build/` and `subprojects/frontends/gamma/gamma-cli/build/`. Once a release is published, you can download `semantifyr-<version>.zip`, `semantifyr-sysml-<version>.zip` or `semantifyr-gamma-<version>.zip` from the [latest release](https://github.com/ftsrg/semantifyr/releases/latest) and unzip it instead.
2. Run `bin/semantifyr` in the installed directory (`bin\semantifyr.bat` on Windows). The distribution bundles the Theta backend, which the launcher puts on `PATH`; install other backends as for the VS Code extension.
3. Verify the bundled smoke model from the installed directory: `bin/semantifyr verify models/smoke`.

The `semantifyr-sysml` and `semantifyr-gamma` distributions carry their semantic library under `libraries/` and a smoke model under `models/smoke/`. `semantifyr-sysml` translates SysML v2 models with the bundled SysIDE fork, which needs Node.js on `PATH`. See [Command Line](cli/index.mdx) for the commands and options, and the [SysML v2 tutorial](tutorials/sysml-v2.md) for verifying SysML v2 models.
