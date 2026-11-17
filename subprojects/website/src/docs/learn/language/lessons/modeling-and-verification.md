---
title: 'Modeling and Verification'
sidebar_position: 1
description: The first lesson of the Semantifyr Language. Model a junction of two traffic lights and the cars that arrive at it, write its requirements as verification cases, and find and fix a flaw with the verifier.
---

import LiveEmbed from '@site/src/components/LiveEmbed';
import LiveLink from '@site/src/components/LiveLink';
import CaseVerdict from '@site/src/components/CaseVerdict';
import { Junction, LightCycle, TraceTimeline } from '@site/src/components/LessonFigures';
import step1 from '@served-examples/oxsts/modeling-and-verification-step1';
import step2 from '@served-examples/oxsts/modeling-and-verification-step2';
import step3 from '@served-examples/oxsts/modeling-and-verification-step3';
import step4 from '@served-examples/oxsts/modeling-and-verification-step4';
import step5 from '@served-examples/oxsts/modeling-and-verification-step5';
import final from '@served-examples/oxsts/modeling-and-verification';

# Modeling and Verification

The goal of this lesson is to model a small system in the Semantifyr Language, write down its requirements as verification cases, and use the verifier to find and fix a flaw in the model.

## Running example

The running example of the lessons is a junction of two roads, one coming from the north and one from the east. Each road has a traffic light, and a sensor in the road that tells whether a car is waiting at the light.

<Junction />

Each light goes through the same cycle: red, green, yellow and red again. Both lights start red, and a light turns green only for a waiting car. The cars come and go as they like, so in every step each sensor may see a car or not.

<LightCycle />

The junction must meet the following requirements:

- The two lights are never green at the same time.
- A light is green only while the other light is red, so that the cars crossing in front of it have already stopped.
- A waiting car can get green. A junction whose lights never change would meet the other two, and nobody could drive through it.

Why verify a junction at all? Its controller is a few lines of logic, and it is easy to convince oneself that it is right. But the cars can arrive in any order, and an unsafe moment may need one unlucky order. Watching the model run shows only the orders that happen to come up. A verifier looks at every order. In this lesson we model the junction and check its requirements with the verifier.

## Modeling a light

Let us start with a single light that only switches between red and green. Its colors are an [enumeration](../specification/foundations/datatypes.md#enumerations):

<LiveEmbed example={step1} show={['LightColor']} />

The light is a [class](../specification/classes-and-behaviour/classes.md). Its state is held in two [variables](../specification/classes-and-behaviour/variables.md): its color, which starts red, and whether its sensor sees a waiting car. What the light does in one step is its main transition. It is written as `redefine tran`, since every class inherits an empty one:

<LiveEmbed example={step1} show={['TrafficLight']} />

The step starts with the environment: [`havoc`](../specification/classes-and-behaviour/operations.md#havoc) gives `carWaiting` any value, so that the verifier tries both a waiting car and an empty road. Then a [`choice`](../specification/classes-and-behaviour/operations.md#choice) takes one of its branches. A branch that starts with an [`assume`](../specification/classes-and-behaviour/operations.md#assumption) can only be taken in a state where the assumption holds. So the light turns green only from red and only for a waiting car, and a green light turns red again. The last branch is empty: the light may also stay as it is, since nothing in the model says how long a light keeps its color.

A model starts in the state given by the initial values of its variables, and every step runs its main transition once. A step in which no branch can be taken does not happen, and the verifier explores every choice that a step can make.

## Verification cases

A question about the model is asked in a verification case. The following case asks whether the light can ever turn green:

<LiveEmbed example={step1} show={['LightCanTurnGreen']} />

`@VerificationCase` marks a class that the verifier checks. The case [extends](../specification/classes-and-behaviour/classes.md#inheritance) the light, so it is the light with a [property](../specification/classes-and-behaviour/properties.md#the-verification-property) added. `EF` asks whether something can ever happen: whether at least one execution reaches a state where the expression holds.

Press **Open** on the block above. It opens the model in Live, the editor and verifier of Semantifyr in the browser. Every **Open** button on this page does the same. Then press **Verify all** under the editor. The case passes, <CaseVerdict example={step1} name="LightCanTurnGreen" />, and below it Live opens its trace, the execution that the verifier found. In the *Steps* view of the trace, the sensor sees a car in the first step, and the light is green after it.

The editor checks the model as you type: a misspelled color is underlined with the problem, and `Ctrl+Space` lists what can be written at the cursor.

Now let us give the light the cycle of the running example.

- The light goes through the whole cycle of the running example.
- A second case, `LightCanTurnYellow`, shows that it can be yellow.

Verify all, and both cases pass: <CaseVerdict example={step2} name="LightCanTurnGreen" /> and <CaseVerdict example={step2} name="LightCanTurnYellow" />. In which step does the light first turn yellow? Then remove the branch from green to yellow and verify again. `LightCanTurnYellow` fails now, and this time Live shows no trace for it: why not? Put the branch back; the light with its whole cycle is <LiveLink example={step2}>this state of the lesson</LiveLink>.

`EF` asks whether something can happen. A requirement is different: it must hold in every state of every execution, and `AG` requires that something always holds. The following case requires that the light is never yellow:

```oxsts
// hide-start
enum LightColor {
    Red,
    Yellow,
    Green,
}

class TrafficLight {
    var color: LightColor := LightColor::Red
    var carWaiting: bool := false

    redefine tran {
        havoc(carWaiting)
        choice {
            assume(color == LightColor::Red && carWaiting)
            color := LightColor::Green
        } or {
            assume(color == LightColor::Green)
            color := LightColor::Yellow
        } or {
            assume(color == LightColor::Yellow)
            color := LightColor::Red
        } or {
        }
    }
}

// hide-end
@VerificationCase
class LightIsNeverYellow : TrafficLight {
    prop {
        return AG (color != LightColor::Yellow)
    }
}
```

Add it to your model and verify it. It fails, and its trace is an execution that breaks the requirement: compare it with the trace of `LightCanTurnYellow`. Then remove the case again.

## Modeling the junction

The junction has two lights, so its model has two colors and two sensors. Let us model it directly, in one class, as we modeled the single light.

- A class `Junction` replaces the light. It has the two lights of the running example, `north` and `east`, and the sensor of each road.
- In each step the cars come and go, and then one of the lights moves on in its cycle, or nothing changes. For now the lights do not look at each other.
- The case `NorthCanTurnGreen` asks whether the north light can turn green.

It passes: <CaseVerdict example={step3} name="NorthCanTurnGreen" />.

## Checking the requirements

Now let us write the first requirement of the running example as a case. Add the case `NeverBothGreen`, which requires that the north and the east light are never green at the same time, and verify all. The case fails, <CaseVerdict example={step3} name="NeverBothGreen" />, so read its trace step by step. The verifier may find a different execution from the one below, but it breaks the requirement in the same way:

<TraceTimeline rows={[{ name: 'north', lamps: ['Red', 'Green', 'Green'] }, { name: 'east', lamps: ['Red', 'Red', 'Green'] }, { name: 'north car', flags: [false, true, true] }, { name: 'east car', flags: [false, false, true] }]} broken="NeverBothGreen" />

Nothing in the model stops a light from turning green while the other one is green. The junction at this point is <LiveLink example={step3}>this state of the lesson</LiveLink>.

Let us make the junction safe. Add a guard to the two branches in which a light turns green, so that `NeverBothGreen` passes. With such a guard both cases pass, <CaseVerdict example={step4} name="NorthCanTurnGreen" /> and <CaseVerdict example={step4} name="NeverBothGreen" />. A guard that never lets a light turn green would also make `NeverBothGreen` pass. Which case shows that your guard is not like that? Compare yours with <LiveLink example={step4}>one solution</LiveLink>.

The second requirement is stricter: a light is green only while the other light is red. Add the case `NorthGreenOnlyWhileEastRed`, which requires it of the north light; "if a, then b" is written `!a || b`. Before you verify, decide whether your guard makes it hold.

With the guard of the linked state, the new case fails: <CaseVerdict example={step5} name="NorthGreenOnlyWhileEastRed" />. If your guard gives another verdict, compare it with that guard. Read the trace to its last state:

<TraceTimeline rows={[{ name: 'north', lamps: ['Red', 'Red', 'Red', 'Green'] }, { name: 'east', lamps: ['Red', 'Green', 'Yellow', 'Yellow'] }, { name: 'north car', flags: [false, false, false, true] }, { name: 'east car', flags: [false, true, true, true] }]} broken="NorthGreenOnlyWhileEastRed" />

<LiveLink example={step5}>This state of the lesson</LiveLink> has that guard and the new case.

## Fixing the model

The problem is in the guard. Find and fix it!

When it is fixed, all three cases pass: <CaseVerdict example={final} name="NorthCanTurnGreen" />, <CaseVerdict example={final} name="NeverBothGreen" /> and <CaseVerdict example={final} name="NorthGreenOnlyWhileEastRed" />. Write the same requirement for the east light, and decide its verdict before you verify it. The junction as this lesson leaves it is <LiveLink example={final}>its final state</LiveLink>.

The next lesson, [Semantic Libraries](semantic-libraries.md), writes the lights on a small statechart library, so that a light no longer spells out its own cycle.
