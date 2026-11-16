---
title: Try in VS Code
slug: /learn/vscode
sidebar_position: 2
description: Verify the cases of a Semantifyr or a Gamma model in VS Code, read their witness and trace, and look at what the compiler makes of a case.
---

# Try in VS Code

The extension lists the verification cases of the Semantifyr and Gamma models of the open folder in VS Code's *Testing* view, runs them on the backends of your machine and opens their witnesses and traces beside the model. [Install](install.md#the-vs-code-extension) builds and installs it.

## Verifying a Case

1. Open a folder that holds a `.semantifyr`, `.gamma` or `.gcd` file. The *Testing* view lists the verification cases of its models; the cases follow unsaved edits, and the generated models under `.artifacts/` are not listed.
2. Run a case from the *Testing* view. Its verdict is the test's result.
3. When the verdict comes with a [witness](../language/specification/library-and-output/inlined-models.md#witnesses), run **Show Witness** on the failure message or in the case's context menu. It opens the back-annotated witness read-only beside the model, for a Gamma case the witness as a Gamma execution trace, and next to it the trace: the calls of each step with their argument values, or the reason they are missing, the state changes of each step, and the time that passed before it when the model has time.
4. After a verdict with a witness the extension [validates](../backends/portfolios-and-verdicts.mdx#validation) the witness, and adds the result, with the backend that validated it, how long that took and how far its verdict is trusted, to the test message and the trace. **Validate Witness** in the same places runs it again.

## Looking at the Compiled Model

- **Inline** above a case of a Semantifyr model compiles it and opens the [inlined model](../language/specification/library-and-output/inlined-models.md) read-only beside the source.
- **Inline (with steps)** also writes the model after each compiler pass to `pipeline/steps/` next to it, in the artifacts directory the settings select.
- **Compile** above an opened inlined model compiles it, unsaved edits included, and opens the result, the flattened model `pipeline/flattened.semantifyr` under `compiled/` in the case's directory, the same way.

## Finding Your Way in a Semantifyr Model

**Show Type Hierarchy** on a class of a Semantifyr model lists its superclasses up to the builtin `Anything` and its subclasses in the workspace; on a transition, property or feature it lists the member it redefines and the members that redefine it.

[The extension's settings](../reference/vscode-settings.mdx) choose the portfolio, the timeout, the validation, how Theta is run, the artifacts and the optimization.
