---
title: Install
slug: /learn/install
sidebar_position: 4
description: Install the VS Code extension or the command-line launchers of Semantifyr.
---

import NodeVersion from '@site/src/components/NodeVersion';

# Install

## The VS Code Extension

Requirements: VS Code 1.109 or newer, Java 25, and at least one backend: [Theta](../backends/theta.mdx), [nuXmv](../backends/nuxmv.mdx), [Spin](../backends/spin.mdx), or [Uppaal](../backends/uppaal.mdx).

Install [Semantifyr](https://marketplace.visualstudio.com/items?itemName=ftsrg.semantifyr) from the Visual Studio Marketplace or [Open VSX](https://open-vsx.org/extension/ftsrg/semantifyr). For SysML v2, also install the [sysml-2ls extension](https://github.com/arminzavada/sysml-2ls/releases/latest).

## The Command Line

Requirements: Java 25, and Node.js <NodeVersion /> for `semantifyr-sysml`. Theta is bundled; nuXmv, Spin, and Uppaal are used when they are on `PATH`.

Download `semantifyr`, `semantifyr-sysml`, or `semantifyr-gamma` from the [latest release](https://github.com/ftsrg/semantifyr/releases/latest), unzip it, and put its `bin/` on `PATH`.

To build either from source, see the repository's [README](https://github.com/ftsrg/semantifyr#build-from-source).
