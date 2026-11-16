---
title: 'The Intersection'
sidebar_position: 1
description: The first lesson of the Semantifyr Language. Model a junction with two traffic lights, write its requirements as verification cases, and find and fix a flaw with the verifier.
---

import LiveEmbed from '@site/src/components/LiveEmbed';
import LiveLink from '@site/src/components/LiveLink';
import CaseVerdict from '@site/src/components/CaseVerdict';
import { Junction, LightCycle, TraceTimeline } from '@site/src/components/LessonFigures';
import step1 from '@served-examples/oxsts/intersection-step1';
import step2 from '@served-examples/oxsts/intersection-step2';
import step3 from '@served-examples/oxsts/intersection-step3';
import step4 from '@served-examples/oxsts/intersection-step4';
import step5 from '@served-examples/oxsts/intersection-step5';
import intersection from '@served-examples/oxsts/intersection';

# The Intersection

The goal of this lesson is to model a small system in the Semantifyr Language, write down its requirements as verification cases, and use the verifier to find and fix a flaw in the model.

Every piece of code on this page opens in Live, the editor and verifier of Semantifyr in the browser, with the **Open** button of its block.

## Running example

The running example of the lessons is a junction of two roads. Cars come from the north and from the east, and each direction has its own traffic light, `north` and `east`.

<Junction />

Each light goes through the same cycle: red, green, yellow and red again. Both lights start red.

<LightCycle />

The junction must meet the following requirements:

- The two lights are never green at the same time.
- A light turns green only while the other light is red, so the cars crossing in front of it have already stopped.
- Each light can turn green. Lights that never change are safe, but nobody can drive.

In this lesson we model the junction and check these requirements with the verifier.

## Modeling a light

Let us start with a single light that only switches between red and green. A model starts with its package, and the colors of the light are an [enumeration](../specification/foundations/datatypes.md#enumerations):

<LiveEmbed example={step1} show={['package', 'LightColor']} />

The light is a [class](../specification/classes-and-behaviour/classes.md). Its state is the [variable](../specification/classes-and-behaviour/variables.md) `color`, which starts at `LightColor::Red`. What the light does in one step is its main transition, written as `redefine tran`, since every class inherits an empty one:

<LiveEmbed example={step1} show={['TrafficLight']} />

The main transition is a [`choice`](../specification/classes-and-behaviour/operations.md#choice) of two branches. A branch starts with an [`assume`](../specification/classes-and-behaviour/operations.md#assumption), and can only be taken in a state where its assumption holds. The assignment `:=` sets the next color.

A model runs as follows:

- It starts in the state that the initial values of its variables give.
- Every step runs the main transition once. A `choice` takes one of the branches that can be taken, and the verifier explores all of them.
- A step in which no branch can be taken does not happen.

## Verification cases

A question about the model is asked in a verification case. The following case asks whether the light can ever turn green:

<LiveEmbed example={step1} show={['LightCanTurnGreen']} />

`@VerificationCase` marks a class that the verifier checks. `LightCanTurnGreen : TrafficLight` [extends](../specification/classes-and-behaviour/classes.md#inheritance) the light, so the case is the light with a [property](../specification/classes-and-behaviour/properties.md#the-verification-property) added. [`EF`](../specification/foundations/expressions.md#temporal-operators), "exists finally", asks whether at least one execution reaches a state where the expression holds.

- Press **Open** on the block above, then **Verify all** under the editor.
- The case passes: <CaseVerdict example={step1} name="LightCanTurnGreen" />. Below the case, Live opens its trace, the execution that the verifier found.
- Observe in the *Steps* view of the trace that the light starts red and turns green in step 1.

The editor checks the model as you type: a misspelled color is underlined with the problem, and `Ctrl+Space` lists what can be written at the cursor.

Now let us give the light the cycle of the running example.

- Add the color `Yellow` to the enumeration.
- Change the main transition so that a green light turns yellow and a yellow light turns red.
- Add a second case, `LightCanTurnYellow`, which asks whether the light can be yellow.
- Verify all. Both cases pass: <CaseVerdict example={step2} name="LightCanTurnGreen" /> and <CaseVerdict example={step2} name="LightCanTurnYellow" />. In which step does the light first turn yellow?
- Remove the branch from yellow to red and verify again. Why does `LightCanTurnYellow` answer as it does? Put the branch back.
- This state of the lesson can be opened in Live via <LiveLink example={step2}>this link</LiveLink>.

`EF` asks whether something can happen. A requirement must hold in every state of every execution, which [`AG`](../specification/foundations/expressions.md#temporal-operators), "always globally", asks. The following case requires that the light is never yellow:

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

`!` is not, `&&` is and, `||` is or, and `!=` is not equal ([expressions](../specification/foundations/expressions.md#boolean-operators)).

- Add the case to your model and verify it.
- Compare its trace with the trace of `LightCanTurnYellow`. The trace of an `AG` case is an execution that breaks the requirement.
- Remove the case again.

## Modeling the intersection

The junction has two lights, so its model has two colors. Let us model it directly, in one class, as we modeled the single light.

- Replace the light with a class `Intersection` that has two color variables, `north` and `east`, both starting red.
- In each step, one of the two lights moves on in its cycle. For now, the lights do not look at each other.
- Add the case `EastCanTurnGreen`, which asks whether the east light can turn green.
- Verify it. It passes: <CaseVerdict example={step3} name="EastCanTurnGreen" />.

## Checking the requirements

Now let us write the first requirement of the running example as a case.

- Add the case `NeverBothGreen`, which requires that `north` and `east` are never green at the same time.
- Verify all. The case fails: <CaseVerdict example={step3} name="NeverBothGreen" />.
- Read its trace step by step. A trace of this case looks like this:

<TraceTimeline states={[['Red', 'Red'], ['Green', 'Red'], ['Green', 'Green']]} broken="NeverBothGreen" />

- Nothing in the model stops a light from turning green while the other one is green. This state of the lesson can be opened via <LiveLink example={step3}>this link</LiveLink>.

Let us make the junction safe.

- Add a guard to the two branches in which a light turns green, so that `NeverBothGreen` passes.
- Verify all. Both cases pass: <CaseVerdict example={step4} name="EastCanTurnGreen" /> and <CaseVerdict example={step4} name="NeverBothGreen" />.
- A guard that never lets a light turn green would also make `NeverBothGreen` pass. Which case tells you that your guard is not such a guard?
- This state of the lesson can be opened via <LiveLink example={step4}>this link</LiveLink>.

The second requirement of the running example is stricter: a light turns green only while the other light is red.

- Add the case `NorthGreenOnlyWhileEastRed`, which requires that `north` is green only while `east` is red. "If a, then b" is written `!a || b`.
- Before you verify, decide whether your guard makes it hold.
- Verify all. With the guard of the linked state, the new case fails: <CaseVerdict example={step5} name="NorthGreenOnlyWhileEastRed" />. If your guard gives another verdict, compare it with that guard.
- Read the trace to its last state. A trace of this case looks like this:

<TraceTimeline states={[['Red', 'Red'], ['Red', 'Green'], ['Red', 'Yellow'], ['Green', 'Yellow']]} broken="NorthGreenOnlyWhileEastRed" />

- This state of the lesson can be opened via <LiveLink example={step5}>this link</LiveLink>.

## Fixing the model

The problem is in the guard. Find and fix it.

- Verify all. All three cases pass: <CaseVerdict example={intersection} name="EastCanTurnGreen" />, <CaseVerdict example={intersection} name="NeverBothGreen" /> and <CaseVerdict example={intersection} name="NorthGreenOnlyWhileEastRed" />.
- Write the same requirement for the east light, and decide its verdict before you verify it.
- The final state of this lesson can be opened via <LiveLink example={intersection}>this link</LiveLink>.

The next lesson builds the lights on a small statechart library, instead of writing out each cycle by hand.
