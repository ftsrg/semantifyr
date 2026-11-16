---
title: 'The Intersection'
sidebar_position: 1
description: The first lesson of the Semantifyr Language. Model a traffic light and an intersection of two directly, require what must always hold, and repair the model from the trace of its failure.
---

import LiveEmbed from '@site/src/components/LiveEmbed';
import CaseVerdict from '@site/src/components/CaseVerdict';
import step1 from '@served-examples/oxsts/intersection-step1';
import step2 from '@served-examples/oxsts/intersection-step2';
import step3 from '@served-examples/oxsts/intersection-step3';
import step4 from '@served-examples/oxsts/intersection-step4';
import step5 from '@served-examples/oxsts/intersection-step5';
import intersection from '@served-examples/oxsts/intersection';

# The Intersection

**What you verify:** that two traffic lights at an intersection are never green together, and a second requirement that the obvious design breaks.

A browser is enough for this lesson. Each model on the page opens in Live, the editor and verifier of Semantifyr, in the place of its code block. From the first task on you build each step yourself, in the editor you have open; a solution under each task opens the step as it is meant to be.

## One Light

A traffic light that switches between red and green. Every file of the language starts with its package, and this one declares the colors as an [enumeration](../specification/foundations/datatypes.md#enumerations):

<LiveEmbed example={step1} show={['package', 'LightColor']} />

The light is a [class](../specification/classes-and-behaviour/classes.md). Its state is one [variable](../specification/classes-and-behaviour/variables.md), `color`, which starts at `LightColor::Red`. Its behavior is its main transition, which it writes as `redefine tran`, since every class inherits an empty one:

<LiveEmbed example={step1} show={['TrafficLight']} />

The main transition is a [`choice`](../specification/classes-and-behaviour/operations.md#choice) of two branches. Each branch starts with an [`assume`](../specification/classes-and-behaviour/operations.md#assumption): a branch whose assumption does not hold in the current state cannot be taken. The assignment `:=` then sets the next color.

This is how a model runs, the one rule everything else builds on:

- The model starts in the state its variables' initial values give.
- Every step runs the main transition once. A `choice` takes any branch that can be taken, and the verifier looks at every one of them.
- A step in which no branch can be taken does not happen.

## A Question

A question about the light is a verification case:

<LiveEmbed example={step1} show={['LightCanTurnGreen']} />

`@VerificationCase` marks a class the verifier checks. `LightCanTurnGreen : TrafficLight` [extends](../specification/classes-and-behaviour/classes.md#inheritance) the light, so the case is the light with a question added: the [property](../specification/classes-and-behaviour/properties.md#the-verification-property) at its bottom. [`EF`](../specification/foundations/expressions.md#temporal-operators), "exists finally", asks whether some execution reaches a state where the expression holds: can the light ever be green?

Press **Open** in the corner of the code block above. The editor opens in its place with the model, and under it a line for the case. Press **Verify all**. The line shows that the case is running, then its verdict:

<details>
<summary>The verdict Live shows</summary>

- <CaseVerdict example={step1} name="LightCanTurnGreen" />

</details>

Once the run ends, the trace of the case opens below its line: the execution the verifier found. Its *Steps* view lists the state at the start and what each step changed. A state, a step, and a trace made of them: that is all a verifier sees of a model.

The editor checks the model as you type: misspell a color and a red underline names the problem, and `Ctrl+Space` offers what can be written where the cursor is. [Try in the Browser](../../start/live/index.md) lists every control of the editor.

## Yellow

Real traffic lights warn before they turn red. Change the light in the editor you have open:

- `Yellow` is a color of the light.
- A green light turns yellow, and a yellow light turns red.
- A second case, `LightCanTurnYellow`, asks whether the light can ever be yellow.

Verify all and compare:

<details>
<summary>The verdicts to expect</summary>

- <CaseVerdict example={step2} name="LightCanTurnGreen" />
- <CaseVerdict example={step2} name="LightCanTurnYellow" />

</details>

In which step is the light first yellow? Then leave out the branch from yellow to red and verify again: why does `LightCanTurnYellow` answer as it does?

<details>
<summary>Solution</summary>

<LiveEmbed example={step2} />

</details>

## Always

`EF` asks for one execution. A requirement asks about all of them: [`AG`](../specification/foundations/expressions.md#temporal-operators), "always globally", asks whether the expression holds in every state of every execution. This case requires that the light is never yellow:

```oxsts
// hide-start
package example::lessons::intersection::always

enum LightColor {
    Red,
    Yellow,
    Green,
}

class TrafficLight {
    var color: LightColor := LightColor::Red

    redefine tran {
        choice {
            assume(color == LightColor::Red)
            color := LightColor::Green
        } or {
            assume(color == LightColor::Green)
            color := LightColor::Yellow
        } or {
            assume(color == LightColor::Yellow)
            color := LightColor::Red
        }
    }
}

// hide-end
@VerificationCase
class LightIsNeverYellow : TrafficLight {
    prop {
        return AG !(color == LightColor::Yellow)
    }
}
```

`!` is not, `&&` and `||` are and and or, and `!=` is not equal ([expressions](../specification/foundations/expressions.md#boolean-operators)). Add the case to the model you have open and verify it. The trace of an `AG` case is an execution that breaks it, as the trace of an `EF` case is one that reaches what it asks for. Compare the trace of `LightIsNeverYellow` with the trace of `LightCanTurnYellow`.

## Two Lights

At an intersection a north light and an east light share the crossing. Model it as you modelled the light, in one class:

- A class `Intersection` replaces the light. It has two color variables, `north` and `east`, both red at the start.
- In each step one of the two lights changes its color, in the cycle of the light.
- A case `EastCanTurnGreen` asks whether east can turn green.
- A case `NeverBothGreen` requires that north and east are never green together.

Predict both verdicts, then verify:

<details>
<summary>The verdicts to expect</summary>

- <CaseVerdict example={step3} name="EastCanTurnGreen" />
- <CaseVerdict example={step3} name="NeverBothGreen" />

</details>

Read the trace of `NeverBothGreen` step by step. What in the model would have had to stop its last step?

<details>
<summary>Solution</summary>

<LiveEmbed example={step3} />

</details>

## A Guard

Make the intersection safe. Change only the branches in which a light turns green:

- `NeverBothGreen` passes.
- `EastCanTurnGreen` still passes.

<details>
<summary>The verdicts to expect</summary>

- <CaseVerdict example={step4} name="EastCanTurnGreen" />
- <CaseVerdict example={step4} name="NeverBothGreen" />

</details>

A guard that never lets a light turn green would make `NeverBothGreen` pass too. Which case tells you that your guard is not one of those?

<details>
<summary>Solution</summary>

<LiveEmbed example={step4} />

</details>

## A Second Requirement

At a real intersection a light turns green only once the crossing traffic has stopped, that is, while the other light is red. Write it down for north:

- A case `NorthGreenOnlyWhileEastRed` requires that north is green only while east is red. "If a, then b" is written `!a || b`.

Does your guard make it hold? Decide, then verify:

<details>
<summary>The verdicts to expect</summary>

- <CaseVerdict example={step5} name="EastCanTurnGreen" />
- <CaseVerdict example={step5} name="NeverBothGreen" />
- <CaseVerdict example={step5} name="NorthGreenOnlyWhileEastRed" />

</details>

These are the verdicts of the solution's guard; if yours gives another verdict, open the solution and verify its guard. Read the trace of `NorthGreenOnlyWhileEastRed` to its last state: what color is east, and which step let north turn green?

<details>
<summary>Solution</summary>

<LiveEmbed example={step5} />

</details>

## The Fix

The problem is in the guard. Find and fix it.

<details>
<summary>The verdicts to expect</summary>

- <CaseVerdict example={intersection} name="EastCanTurnGreen" />
- <CaseVerdict example={intersection} name="NeverBothGreen" />
- <CaseVerdict example={intersection} name="NorthGreenOnlyWhileEastRed" />

</details>

Does east, too, turn green only while north is red? Write the case, and predict its verdict before you verify it.

<details>
<summary>Solution</summary>

<LiveEmbed example={intersection} />

</details>

## What You Can Do Now

You can model a system directly in a class, ask whether a state can be reached, require that something holds in every state, read the trace of a failure back into the model and repair it. The guard that kept the lights from being green together looked right, and it took a second requirement to show what it let through. The next lesson writes once, as a small library, what a light is made of, and builds lights on it as data: [A Statechart Library](library.md).
