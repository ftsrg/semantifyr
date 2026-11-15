---
title: Installing It
slug: /learn/install
sidebar_position: 4
description: Install the VS Code extension for OXSTS and Gamma, or the command-line launchers for OXSTS, SysML v2 and Gamma, built from the repository.
---

import NodeVersion from '@site/src/components/NodeVersion';
import Planned from '@site/src/components/Planned';

# Installing It

Semantifyr runs on your machine as a VS Code extension, for OXSTS and Gamma, and as three command-line launchers, `semantifyr` for OXSTS, `semantifyr-sysml` for SysML v2 and `semantifyr-gamma` for Gamma. The extension edits and verifies OXSTS and Gamma models and the launchers models of all three languages, both with the same compiler as [Live](live/index.md); verification runs on the backends installed on your machine. Until a release is published, both are built from a clone of the [repository](https://github.com/ftsrg/semantifyr), which needs Git and a Java 25 runtime and downloads everything else.

## The VS Code Extension

Use it to work on models in your own folders and repositories.

**Requirements**

- [Visual Studio Code](https://code.visualstudio.com/) 1.109 or newer
- A Java 25 runtime, found through `JAVA_HOME` or `PATH`, which runs the bundled language servers
- At least one backend, [Theta](../backends/theta.mdx), [Spin](../backends/spin.mdx), [nuXmv](../backends/nuxmv.mdx) or [Uppaal](../backends/uppaal.mdx), installed as its page says

**Install**

1. Build the extension with `./gradlew :semantifyr-vscode:bundleExtension`, which writes `subprojects/semantifyr-vscode/build/semantifyr-<version>.vsix`.
2. In VS Code, run **Extensions: Install from VSIX...** from the Command Palette (`Ctrl+Shift+P` / `Cmd+Shift+P`) and select the `.vsix` file.
3. Open a folder that holds an OXSTS or a Gamma model, a `.oxsts`, `.gamma` or `.gcd` file. The extension starts by itself and lists the model's verification cases in the *Testing* view.

[Verifying in VS Code](vscode.md) shows how to verify a case and read its witness and trace, and [its settings](../reference/vscode-settings.mdx) choose the portfolio, the timeout and the rest.

<Planned>
Publication on the Visual Studio Marketplace is planned.
</Planned>

## The Command Line

Use it in scripts and CI. Each launcher has a distribution of its own with the Theta backend, its semantic library and a smoke model, and needs a Java 25 runtime.

1. Build the distribution with `./gradlew :semantifyr-cli:installDist`, which installs it to `subprojects/semantifyr-cli/build/install/semantifyr`; `./gradlew :semantifyr-cli:distZip` packs the same files into `subprojects/semantifyr-cli/build/distributions/semantifyr-<version>.zip`. `:sysml-cli` and `:gamma-cli` build `semantifyr-sysml` and `semantifyr-gamma` the same way, under `subprojects/frontends/sysml/sysml-cli/build/` and `subprojects/frontends/gamma/gamma-cli/build/`. The first build downloads its dependencies, the SysIDE fork among them, and takes several minutes.
2. Run `bin/semantifyr` in the installed directory (`bin\semantifyr.bat` on Windows). The distribution bundles the Theta backend, which the launcher puts on `PATH`; install the other backends as for the VS Code extension.
3. Verify the bundled smoke model from the installed directory: `bin/semantifyr verify models/smoke`.

The `semantifyr-sysml` and `semantifyr-gamma` distributions carry their semantic library under `libraries/` and a smoke model under `models/smoke/`. `semantifyr-sysml` translates SysML v2 models with the bundled SysIDE fork, which needs Node.js <NodeVersion /> or newer on `PATH`. [Trying It on the Command Line](command-line.mdx) walks through a run of `semantifyr` and [SysML v2 on the Command Line](../frontends/sysml-v2/command-line.mdx) one of `semantifyr-sysml`, and [the command line's reference](../reference/command-line.mdx) lists the commands and options.

<Planned>
A hosted JupyterHub environment that runs VS Code in the browser with the Semantifyr extension, the Theta backend, and example models for SysML v2 and Gamma.
</Planned>
