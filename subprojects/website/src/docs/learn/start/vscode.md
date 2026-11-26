---
title: Try in VS Code
slug: /learn/vscode
sidebar_position: 2
description: Verify the cases of a Semantifyr or a Gamma model in VS Code.
---

# Try in VS Code

The VS Code lets you edit and verify Semantifyr and Gamma models in your workspace, using the backends installed on your machine. To install it, see [Install](install.md#the-vs-code-extension).

For SysML v2, install the [SysIDE fork's extension](https://github.com/arminzavada/sysml-2ls/releases/latest) beside it. It compiles a SysML v2 model into a Semantifyr model.

Usage:

1. Open a folder that holds a `.semantifyr`, `.gamma`, or `.gcd` file. The *Testing* view lists the verification cases of its models.
2. Run a case from the *Testing* view. Its verdict is the test's result.
3. If a case has a witness, run **Show Witness** on its result to open the witness and the trace beside the model.

You can choose the portfolio, the timeout, and the backends' options in the [extension's settings](../reference/vscode-settings.mdx).
