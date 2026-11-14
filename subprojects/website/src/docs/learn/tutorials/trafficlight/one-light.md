---
title: 'One Light'
sidebar_position: 1
description: The first OXSTS lesson. Model one traffic light, ask whether it can turn yellow, and read the trace that answers.
---

import LiveEmbed from '@site/src/components/LiveEmbed';
import CaseVerdict from '@site/src/components/CaseVerdict';
import step1 from '@served-examples/oxsts/one-light';

# One Light

**What you verify:** that a traffic light can turn yellow, and how it gets there.

A browser is enough for this lesson. Each model on the page opens in Live, the editor and verifier of Semantifyr, in the place of its code block.

## The Light

A traffic light has three colors. Every OXSTS file starts with its package, and this one declares the colors as an [enumeration](../../reference/language/datatypes.md#enumerations):

<LiveEmbed example={step1} show={['package', 'LightColor']} />

Semicolons may end a statement, and nothing needs one.

The light itself is a [class](../../reference/language/classes.md). Its state is one [variable](../../reference/language/variables.md), `color`, which starts at `LightColor::Red`. Its behavior is its main transition. Every class extends [`Anything`](../../reference/language/classes.md#the-anything-base-class), which declares an empty main transition and an empty `init`, so the light writes its own as `redefine tran`:

<LiveEmbed example={step1} show={['TrafficLight']} />

The main transition is a [`choice`](../../reference/language/operations.md#choice) of three branches. Each branch starts with an [`assume`](../../reference/language/operations.md#assumption): a branch whose assumption does not hold in the current state cannot be taken. The assignment `:=` then sets the next color.

This is how a model runs, the one rule everything else builds on:

- This model has no `init` transition of its own, so it starts in the state its variables' initial values give.
- Every step runs the main transition once. A `choice` takes any branch that can be taken, and the verifier looks at every one of them.
- A step in which no branch can be taken does not happen.

So the light goes from red to green to yellow and back to red, one color a step.

## The Question

`@VerificationCase` makes the light a case that the verifier checks, and the [property](../../reference/language/properties.md#the-verification-property) at the bottom is the question it asks. [`EF`](../../reference/language/expressions.md#temporal-operators), "exists finally", asks whether some execution reaches a state where the expression holds: can the light ever be yellow?

Before you run it, decide what you expect, and how many steps you think the light needs.

Press **Run** in the corner of the code block above. The editor opens in its place with the model, and under it a line for the case, `TrafficLight`. Press **Verify all**. The line shows that the case is running, then its verdict:

<details>
<summary>The verdict Live shows</summary>

- <CaseVerdict example={step1} name="TrafficLight" />

</details>

## The Trace

Once the run ends, the trace of the case opens below its line by itself: the execution the verifier found. A click on the line closes it, and another opens it again. Its *Steps* view lists the state at the start and the change of each step: `color` starts at `Red`, turns `Green` in step 1 and `Yellow` in step 2.

The trace of an `EF` case is an execution that reaches what the case asks for. The other views show the same execution: *Witness* as an OXSTS model, and *Calls* the calls of transitions marked to be traced, of which this model has none yet.

The editor checks the model as you type: misspell a color and a red underline names the problem, and `Ctrl+Space` offers what can be written where the cursor is. [Live's screen](../../tools/live.md) lists every control of the editor.

## What You Can Do Now

You can write a class with a variable and a main transition, make it a case, and ask whether a state is reachable. The next lesson puts two lights at an intersection and asks something that must hold in every state: [The Intersection](intersection.md).
