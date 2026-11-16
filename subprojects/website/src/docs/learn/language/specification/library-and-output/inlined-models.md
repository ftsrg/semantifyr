---
title: Inlined Models
slug: /learn/language/specification/inlined-models
sidebar_position: 13.5
description: The inlined model, the form in which the compiler writes a compiled case and a witness, and the witness of a verdict, its form, its time and the report it is derived from.
---

import CodeBlock from '@theme/CodeBlock';
import witnessModel from '@sysml-reference-trace/crossroads-green.witness.semantifyr';

# Inlined Models

An inlined model is the form in which the compiler writes a case after each of its phases and in which a witness is written ([Artifacts](../../../reference/artifacts.mdx)): one file that names the class it comes from and holds the whole model a backend needs, with no class of its own. It is a Semantifyr model: the editors open it, and it validates, compiles and verifies as any other model. It is written by the tools, not by hand.

## Syntax

An inlined model opens with `inlined oxsts of`, or `witness of` for a [witness](#witnesses), and the qualified name of the class; a witness whose times are not all whole continues with `timescale` and an integer. Then come, in this order, its variables, optionally a feature that holds the instance of the class, the `init` transition, the main transition, each with its branches separated by `or`, and the property, an expression in `prop { }` without `return`. It has no package and no imports. The feature is named `root` in the instantiated and the inlined model and in the witness, and the members of instances are named by their paths from it, such as `root.counter.value`; the flattened model has no feature left.

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

Its variables are the state, `init` gives the initial states from the variables' initializers, the main transition relates a state to the next after the [wait](../classes-and-behaviour/time.md#the-wait-before-a-step) before it, and the property is checked on the reachable states. An inlined model the compiler writes for a case has the case's meaning; a witness has only the execution it records, so its property passes when the execution shows the verdict.

## Witnesses

A witness is the run behind a verdict, a Failed `AG` or a Passed `EF` property, which the verifier back-annotates into a Semantifyr model, `witness.semantifyr`, in the case's directory: an inlined model, `witness of` the verified class, that has exactly that execution. It declares `var step: int := 0` and holds the class as `root`. Its `init` assumes `step == 0`, runs the class's `init`, assumes the value of every variable in the initial state and sets the next step; each further step is a branch of its main transition that assumes the step's number, runs the class's main transition, assumes the values of the state after it and sets the number of the next step. Its property is the class's own for a passed `EF` and its negation for a failed `AG`.

A witness of a model with [clocks](../classes-and-behaviour/time.md) also declares `var time: clock := 0`, a clock of its own that is never reset, and each step assumes after it the time passed since the start and the value of every clock the state holds. When those amounts are not all whole, each is multiplied by their common denominator, which the header names after `timescale`, and the clock bounds of the class count that many times. Here the blinker of [Time](../classes-and-behaviour/time.md#the-wait-before-a-step) switches on after a wait of 5/2:

```oxsts
package example::blinker

class Blinker {
    var on: bool := false
    var since: clock := 0

    redefine tran {
        assume (since <= 5)
        assume (since >= 2)
        on := !on
        since := 0
    }

    prop {
        return EF (on)
    }
}
```

```oxsts
witness of example::blinker::Blinker timescale 2

var step: int := 0

var time: clock := 0

contains root: example::blinker::Blinker

init {
    assume(step == 0)
    inline root.init()
    assume(root.on == false)
    assume(root.since == 0)
    step := 1
}

tran {
    assume(step == 1)
    inline root.main()
    assume(time == 5)
    assume(root.on == true)
    assume(root.since == 0)
    step := 2
}

prop {
    root.prop()
}
```

The verifier derives the witness from the [report](../../../reference/artifacts.mdx#the-report) of the run, which holds the state and the traced calls of each step and from which the editors and the frontends show the trace.

The SysML v2 reference witness, of the case `TrafficLightABehaviorNormalGreenUnreachableViolated` of the crossroads model, which the SysML v2 frontend's tests check:

<details>
<summary><code>witness.semantifyr</code></summary>

<CodeBlock language="oxsts">{witnessModel}</CodeBlock>

</details>
