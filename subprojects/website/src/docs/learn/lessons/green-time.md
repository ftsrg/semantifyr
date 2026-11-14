---
title: 'The Minimum Green Time'
sidebar_position: 3
description: The third OXSTS lesson. Hold a green light for at least three steps with an integer counter, and check it with a case that counts on its own.
---

import LiveEmbed from '@site/src/components/LiveEmbed';
import CaseVerdict from '@site/src/components/CaseVerdict';
import step1 from '@served-examples/oxsts/green-time-step1';
import step2 from '@served-examples/oxsts/green-time-step2';
import greenTime from '@served-examples/oxsts/green-time';

# The Minimum Green Time

**What you verify:** that north can still turn yellow once it counts its green steps, and that it stays green for at least three steps every time, checked by a case that counts on its own.

This lesson starts from the fixed intersection of [The Intersection](intersection.md), where a light switches only while the other is red.

## Counting Green Steps

So that traffic can pass, a light that turns green stays green for at least three steps. The light counts them in an integer variable, `greenFor`:

<LiveEmbed example={step1} show={['TrafficLight']} />

The main transition has a fourth branch. While the light is green and has counted fewer than three steps, it stays green and adds one to the counter with [arithmetic](../reference/language/expressions.md#arithmetic-operators); once it has counted three, it may turn yellow. The guard `greenFor < 3` also bounds the counter: it never goes above three, the bound a backend such as [Spin](../reference/backends/spin.mdx) needs on an integer.

Can north still turn yellow, and how many steps does it take now?

<LiveEmbed example={step1} show={['NorthCanTurnYellow']} />

Run it:

<details>
<summary>The verdict Live shows</summary>

- <CaseVerdict example={step1} name="NorthCanTurnYellow" />

</details>

The trace shows the numbers: north turns green, `north.greenFor` goes from 0 to 1, 2 and 3 in the steps after it, and north turns yellow.

## A Case That Counts on Its Own

That trace is one execution. The requirement is about every green phase of every execution, and asking the light's own counter about it would check the counter against itself. The case counts instead, in variables of its own:

<LiveEmbed example={step2} show={['NorthGreenForThreeSteps']} />

Its main transition remembers whether north was green before the step in a [local variable](../reference/language/operations.md#local-variable), `wasGreen`, which lives only in the step that declares it and may read the state. It runs the intersection's step, then looks again. While north stays green, the case counts, up to three; when north leaves green, `leftGreenEarly` tells whether it left before three steps. The [`if`](../reference/language/operations.md#if) is decided at run time, in every step. `AG !leftGreenEarly` asks that it never does.

Predict, then run the step:

<details>
<summary>The verdicts Live shows</summary>

- <CaseVerdict example={step2} name="NorthCanTurnYellow" />
- <CaseVerdict example={step2} name="NorthGreenForThreeSteps" />

</details>

## Reading the Trace

Open the trace of `NorthGreenForThreeSteps` and follow `north.greenFor` and the case's `stepsGreen` through *Steps*. The first green phase is the one you saw above. Then north turns yellow and red, and `greenFor` stays at 3. When north turns green the second time the counter still says 3, in the next state north is yellow, and in that last state `leftGreenEarly` is `true`.

The counter starts at 0 when the model starts, and nothing sets it back.

## The Fix

Every change of color now goes through one [transition with a parameter](../reference/language/transitions.md#declaring-a-transition), `switchTo`, which sets the color and starts the count again, so no branch can forget it. Each branch calls it with an inline call and an argument; inside it, `self.color` is the variable and `color` the parameter, as in `isColor`. `@Trace` moves to `switchTo`, so *Calls* names each switch with its color.

<LiveEmbed example={greenTime} show={['TrafficLight']} />

Run the fixed model:

<details>
<summary>The verdicts Live shows</summary>

- <CaseVerdict example={greenTime} name="NorthCanTurnYellow" />
- <CaseVerdict example={greenTime} name="NorthGreenForThreeSteps" />

</details>

The whole model:

<LiveEmbed example={greenTime} />

## What You Can Do Now

You can keep a number in a model, bound it by a guard, compute with it, and check a requirement over time with a case that keeps its own count. The next lesson writes how a statechart runs once, as a small library, and builds two different lights on it: [A Statechart Library](library.md).
