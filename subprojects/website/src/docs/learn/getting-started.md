---
title: Getting Started
sidebar_position: 1
---

# Getting Started

There are four ways to start using Semantifyr. Pick whichever fits your situation. All four give you the same language and the same compiler; the verification backends available depend on the environment.

## Option A: Try in the browser (no installation)

The fastest way to try Semantifyr. The **in-browser live editor** is a lightweight Monaco-based editor connected to the Semantifyr language server. It supports syntax highlighting, diagnostics, content assist, and an integrated verification panel where you can discover, run, and inspect verification cases individually.

[Open the live editor](https://live.semantifyr.org)

Sessions are ephemeral. Use the **Copy link** button in the toolbar to save your work as a shareable URL.

## Option B: Use the JupyterHub environment (full IDE, no installation)

If you want a full-fledged VS Code environment with multi-file project support, the hosted JupyterHub environment is a good fit. It runs a VS Code instance in your browser with the Semantifyr extension, the Theta backend, and example models for SysML v2 and Gamma already in place.

[Launch the JupyterHub environment](https://jupyter.semantifyr.org)

You'll get a personal workspace with everything wired up: no Java install, no extension setup, no project scaffolding.

:::note Access requires an account
The JupyterHub environment is a managed deployment, so you need a user account to log in. If you don't have one yet, [contact the maintainers](https://github.com/ftsrg/semantifyr/blob/main/CONTRIBUTORS.md) to request access.
:::

## Option C: Install the VS Code extension (for local work)

The Semantifyr VS Code extension bundles the language servers and the compiler. Verification runs on the backends installed on your machine. Use it when you want to work on your own machine, keep models in your own repository, or integrate Semantifyr into a longer-lived project.

**Requirements**

- [Visual Studio Code](https://code.visualstudio.com/) 1.109 or newer
- A Java 25 runtime, found through `JAVA_HOME` or `PATH` (the extension uses it to run the bundled language servers)
- At least one verification backend: Theta (`theta-xsts-cli` on `PATH`, or Docker), Spin (`spin` and a C compiler on `PATH`), nuXmv (`nuXmv` on `PATH`) or Uppaal (`verifyta` on `PATH`)

**Install**

1. Open the Extensions view in VS Code (`Ctrl+Shift+X` / `Cmd+Shift+X`).
2. Search for **Semantifyr** (publisher: *ftsrg*) and click **Install**.
3. Open any folder containing a `.oxsts` or `.gamma` file. The extension activates automatically and provides syntax highlighting, diagnostics, and the verification cases in the *Testing* view.

## Option D: Install the command-line interface (for scripts and CI)

The `semantifyr` command lists, compiles and verifies the verification cases of OXSTS models from a terminal. It needs a Java 25 runtime.

1. Download `semantifyr-<version>.zip` from the [latest release](https://github.com/ftsrg/semantifyr/releases/latest) and unzip it.
2. Run `semantifyr-<version>/bin/semantifyr` (`bin\semantifyr.bat` on Windows). The zip bundles the Theta backend, which the Linux and macOS launcher puts on `PATH`; install other backends as for the VS Code extension.
3. Verify the bundled smoke model: `semantifyr-<version>/bin/semantifyr verify semantifyr-<version>/models/smoke`.

The zip also carries the Gamma and SysML v2 semantic libraries under `libraries/`. See [Command Line](cli/index.mdx) for the commands and options.
