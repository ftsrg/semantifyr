---
title: Getting Started
sidebar_position: 1
---

# Getting Started

There are three ways to start using Semantifyr. Pick whichever fits your situation — all three give you the same language, the same compiler, and the same Theta backend.

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

:::caution Work in progress
The VS Code extension is not yet published on the Marketplace. This section describes the planned setup for when it becomes available.
:::

The Semantifyr VS Code extension bundles the language server, the compiler, and the Theta verification backend. Use it when you want to work on your own machine, keep models in your own repository, or integrate Semantifyr into a longer-lived project.

**Requirements**

- [Visual Studio Code](https://code.visualstudio.com/) 1.93 or newer
- A Java 21+ runtime on your `PATH` (the extension uses it to run the bundled Semantifyr CLI)

**Install**

1. Open the Extensions view in VS Code (`Ctrl+Shift+X` / `Cmd+Shift+X`).
2. Search for **Semantifyr** (publisher: *ftsrg*) and click **Install**.
3. Open any folder containing a `.oxsts` file. The extension activates automatically and provides syntax highlighting, diagnostics, and the *Verify* command.
