---
title: Inlined Models
slug: /learn/language/reference/inlined-models
sidebar_position: 3
description: The inlined model, the one-file form of a compiled case, and the witness, the inlined model of the run behind a verdict.
---

import CodeBlock from '@theme/CodeBlock';
import timedModel from '@oxsts-witness-reference/blinker.semantifyr';
import timedWitness from '@generated-witness/blinker.witness.semantifyr';

# Inlined Models

An inlined model is a transition system in one file: variables, an `init` transition, a main transition, and a property, with no classes of its own. The compiler writes a case in this form after each [phase](../../compilation/index.mdx) of the compilation, and a witness is an inlined model too.

## Syntax

An inlined model starts with `inlined oxsts of` and the class it is compiled from. Then come, in this order, its variables, optionally a feature that holds an instance of the class, `init`, the main transition, and the property. This model runs a counter and counts its steps:

```oxsts hidden
package example::counter

class Counter {
  var value: int := 0

  redefine tran {
    value := value + 1
  }
}
```

```oxsts
inlined oxsts of example::counter::Counter

var steps: int := 0

contains root: example::counter::Counter

init {
  inline root.init()
}

tran {
  inline root.main()
  steps := steps + 1
}

prop {
  AG root.value == steps
}
```

The property is an expression in `prop { }`, without `return`. The compiler names the feature `root`, the root instance of the [universe](../../compilation/index.mdx#instantiation), and paths from it, such as `root.value`, name the members of the instance and of its parts.

## Names

An inlined model has no package and imports only the standard library. It names every other declaration by its qualified name, as in `example::counter::Counter`. A class from a file without a package is named by its simple name, and so are the other declarations of that file.

## Meaning

An inlined model is verified like a verification case: its `init` runs once, then its main transition again and again, and its property is checked. Its state is its variables and the variables of the instances under its feature. An inlined model that the compiler writes for a case means the same as the case.

## Witnesses

A witness is the inlined model of one run: the run that makes an `AG` property fail or an `EF` property pass. It starts with `witness of` and the verified class.

A witness declares `var step: int := 0`, the number of the next step, and holds the class as `root`. Its `init` assumes `step == 0`, runs the class's `init`, assumes the values of the variables in the first state of the run, and sets `step` to 1. Each later step of the run is a branch of the main transition. The branch assumes that `step` holds its number, runs the class's main transition, assumes the values of the variables after it, and sets `step` to the next number. Only the branch whose number `step` holds can run, so the witness has exactly the recorded run.

The property is the class's own, `root.prop()`, when an `EF` property passed, and its negation, `!root.prop()`, when an `AG` property failed. So the witness passes only if the class can take the run and the run shows the verdict.

If the flat model keeps a [clock](../classes-and-behaviour/time.md), the witness also declares `var time: clock := 0`, which is never reset. Each step then also assumes the value of `time`, the time since the start. If the times of the run are not all whole numbers, the header ends with `timescale` and an integer n. The witness then counts time in units of 1/n, and the clock bounds of the class still count whole units.

This case asks for a switch strictly between 2 and 3 after the start:

<CodeBlock language="oxsts" title="blinker.semantifyr">{timedModel}</CodeBlock>

No whole time lies strictly between 2 and 3, so the run waits 5/2, and its witness counts time in halves:

<CodeBlock language="oxsts" title="witness.semantifyr">{timedWitness}</CodeBlock>
