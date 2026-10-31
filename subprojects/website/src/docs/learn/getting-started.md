---
title: Getting Started
sidebar_position: 1
---

# Getting Started

New to Semantifyr? Start with the [Tutorials](tutorials/index.md): the first ones run in the browser with nothing to install.

There are three ways to start using Semantifyr today, and a fourth is planned. Pick whichever fits your situation. All of them give you the same language and the same compiler; the verification backends available depend on the environment.

## Option A: Try in the browser (no installation)

The fastest way to try Semantifyr. The **in-browser live editor** is a lightweight Monaco-based editor connected to the Semantifyr language server. It supports syntax highlighting, diagnostics, content assist, and an integrated verification panel where you can discover, run, and inspect verification cases individually.

[Open the live editor](https://live.semantifyr.org)

Sessions are ephemeral. Use the **Copy link** button in the toolbar to save your work as a shareable URL.

:::note
If the live editor stays blank after an update of the server, your browser is still running the previous version it cached. Delete the stored site data of `live.semantifyr.org` in your browser's site settings and reload.
:::

## Option B: Use the JupyterHub environment (full IDE, no installation)

:::info[Planned]
A hosted JupyterHub environment that runs VS Code in the browser with the Semantifyr extension, the Theta backend, and example models for SysML v2 and Gamma. It is offline until it is rebuilt; until then, use the live editor or a local install.
:::

## Option C: Install the VS Code extension (for local work)

The Semantifyr VS Code extension bundles the language servers and the compiler. Verification runs on the backends installed on your machine. Use it when you want to work on your own machine, keep models in your own repository, or integrate Semantifyr into a longer-lived project.

**Requirements**

- [Visual Studio Code](https://code.visualstudio.com/) 1.109 or newer
- A Java 25 runtime, found through `JAVA_HOME` or `PATH` (the extension uses it to run the bundled language servers)
- At least one verification backend: Theta (`theta-xsts-cli` on `PATH`, or Docker), Spin (`spin` and a C compiler on `PATH`), nuXmv (`nuXmv` on `PATH`) or Uppaal (`verifyta` on `PATH`)

**Install**

1. Build the extension from a clone of the [repository](https://github.com/ftsrg/semantifyr) with `./gradlew :semantifyr-vscode:bundleExtension`, which writes `subprojects/semantifyr-vscode/build/semantifyr-<version>.vsix`. The build needs Git and a Java 25 runtime and downloads everything else. Once a release is published, you can download the `.vsix` from the [latest release](https://github.com/ftsrg/semantifyr/releases/latest) instead.
2. In VS Code, run **Extensions: Install from VSIX...** from the Command Palette (`Ctrl+Shift+P` / `Cmd+Shift+P`) and select the `.vsix` file.
3. Open any folder containing a `.oxsts` or `.gamma` file. The extension activates automatically and provides syntax highlighting, diagnostics, and the verification cases in the *Testing* view.

:::info[Planned]
Publication on the Visual Studio Marketplace is planned.
:::

## Option D: Install the command-line interface (for scripts and CI)

The `semantifyr` command lists, compiles and verifies the verification cases of OXSTS models from a terminal. It needs a Java 25 runtime.

1. Build the distribution from a clone of the [repository](https://github.com/ftsrg/semantifyr) with `./gradlew :semantifyr-cli:installDist`, which installs it to `subprojects/semantifyr-cli/build/install/semantifyr`; `./gradlew :semantifyr-cli:distZip` packs the same files into `subprojects/semantifyr-cli/build/distributions/semantifyr-<version>.zip`. Once a release is published, you can download `semantifyr-<version>.zip` from the [latest release](https://github.com/ftsrg/semantifyr/releases/latest) and unzip it instead.
2. Run `bin/semantifyr` in the installed directory (`bin\semantifyr.bat` on Windows). The distribution bundles the Theta backend, which the Linux and macOS launcher puts on `PATH`; install other backends as for the VS Code extension.
3. Verify the bundled smoke model from the installed directory: `bin/semantifyr verify models/smoke`.

The distribution also carries the Gamma and SysML v2 semantic libraries under `libraries/`. See [Command Line](cli/index.mdx) for the commands and options, and the [SysML v2 tutorial](tutorials/sysml-v2.md) for verifying SysML v2 models.
