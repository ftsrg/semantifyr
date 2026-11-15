---
title: Inlined Models
slug: /learn/oxsts/language/inlined-models
sidebar_position: 13.5
description: The inlined model, the form in which the compiler writes a compiled case and a witness, and the witness of a verdict, its form and the files beside it.
---

import CodeBlock from '@theme/CodeBlock';
import witnessModel from '@sysml-reference-trace/crossroads-green.witness.oxsts';

# Inlined Models

An inlined model is the form in which the compiler writes a case after each of its phases and in which a witness is written ([Artifacts](../../../reference/artifacts.mdx)): one file that names the class it comes from and holds the whole model a backend needs, with no class of its own. It is OXSTS: the editors open it, and it validates, compiles and verifies as any other model. It is written by the tools, not by hand.

## Syntax

An inlined model opens with `inlined oxsts of`, or `witness of` for a [witness](#witnesses), and the qualified name of the class. Then come, in this order, its variables, optionally a feature that holds the instance of the class, the `init` transition, the main transition, each with its branches separated by `or`, and the property, an expression in `prop { }` without `return`. It has no package and no imports. The feature is named `root` in the instantiated and the inlined model and in the witness, and the members of instances are named by their paths from it, such as `root.counter.value`; the flattened model has no feature left.

```oxsts
inlined oxsts of semantifyr::Anything

var counter: int := 0

init {
    counter := 0
}

tran {
    choice {
        counter := counter + 1
    } or {
        counter := 0
    }
}

prop {
    AG counter >= 0
}
```

## Static Rules

The rules of the rest of the reference hold, with these differences:

- A variable carries a type and an initializer, as everywhere.
- The class is found among the declarations the editor or the compiler has loaded, and a class of a file without a package by its simple name.
- The validator gives no hint in an inlined model.
- A temporal operator stands at the root of the property, as it does at the root of a property's `return`.

## Meaning

Its variables are the state, `init` gives the initial states from the variables' initializers, the main transition relates a state to the next, and the property is checked on the reachable states. An inlined model the compiler writes for a case has the case's meaning; a witness has only the execution it records, so its property passes when the execution shows the verdict.

## Witnesses

A witness is the run behind a verdict, a Failed `AG` or a Passed `EF` property, which the verifier back-annotates into OXSTS as `witness.oxsts` in the case's directory: an inlined model, `witness of` the verified class, that has exactly that execution. It declares `var step: int := 0` and holds the class as `root`. Its `init` assumes `step == 0`, runs the class's `init`, assumes the value of every variable in the initial state and sets the next step; each further step is a branch of its main transition that assumes the step's number, runs the class's main transition, assumes the values of the state after it and sets the number of the next step. Its property is the class's own for a passed `EF` and its negation for a failed `AG`.

Beside it, [`witness.json`](../../../reference/artifacts.mdx#witnessjson) holds the state of each step and [`trace.json`](../../../reference/artifacts.mdx#tracejson) the traced calls of each step, from which the editors and the frontends show the trace.

The SysML v2 reference witness, of the case `TrafficLightABehaviorNormalGreenUnreachableViolated` of the crossroads model, which the SysML v2 frontend's tests check:

<details>
<summary><code>witness.oxsts</code></summary>

<CodeBlock language="oxsts">{witnessModel}</CodeBlock>

</details>
