---
title: The VS Code Extension
sidebar_position: 4
description: Verify the cases of an OXSTS or a Gamma model in VS Code, read their witness and trace, and look at what the compiler makes of a case.
---

# The VS Code Extension

The extension lists the verification cases of the OXSTS and Gamma models of the open folder in VS Code's *Testing* view, runs them on the backends of your machine and opens their witnesses and traces beside the model. [Installing It](../start/install.md#the-vs-code-extension) builds and installs it.

## Verifying a Case

1. Open a folder that holds a `.oxsts`, `.gamma` or `.gcd` file. The *Testing* view lists the verification cases of its models; the cases follow unsaved edits, and the generated models under `.artifacts/` are not listed.
2. Run a case from the *Testing* view. Its verdict is the test's result.
3. When the verdict comes with a witness, the evidence of a violated invariant or the path to a reachable state, run **Show Witness** on the failure message or in the case's context menu. It opens the back-annotated witness read-only beside the model, for a Gamma case the witness as a Gamma execution trace, and next to it the trace: the calls and the state changes of each step.
4. After a verdict with a witness the extension validates the witness by verifying it again, and adds the result to the test message and the trace. **Validate Witness** in the same places runs it again.

## Looking at the Compiled Model

- **Inline** above an OXSTS case compiles it and opens the [inlined model](../reference/language/inlined-models.md) read-only beside the source.
- **Inline (with steps)** also writes the model after each compiler pass to `pipeline/steps/` next to it, in the artifacts directory the settings select.
- **Compile** above an opened inlined model compiles it, unsaved edits included, and opens the result, `compiled.oxsts`, the same way.

## Finding Your Way in OXSTS

**Show Type Hierarchy** on an OXSTS class lists its superclasses up to the builtin `Anything` and its subclasses in the workspace; on a transition, property or feature it lists the member it redefines and the members that redefine it.

[The extension's settings](../reference/editors.mdx) choose the portfolio, the timeout, the validation, how Theta is run, the artifacts and the optimization.
