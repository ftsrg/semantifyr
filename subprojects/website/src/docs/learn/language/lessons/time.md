---
title: 'Time'
sidebar_position: 4
description: The fourth lesson of the Semantifyr Language. Write a requirement on how long a light stays green, extend the statechart library with time, and let a light say how long it lasts as data.
---

import LiveEmbed from '@site/src/components/LiveEmbed';
import LiveLink from '@site/src/components/LiveLink';
import CaseVerdict from '@site/src/components/CaseVerdict';
import { TraceTimeline } from '@site/src/components/LessonFigures';
import variants from '@served-examples/oxsts/semantic-variants-step1';
import step1 from '@served-examples/oxsts/time-step1';
import step2 from '@served-examples/oxsts/time-step2';
import final from '@served-examples/oxsts/time';

# Time

The goal of this lesson is to extend the statechart library with a new concept, time, so that a model can say as data how long a light stays in a state.

## Running example

Why would we count steps? Because real requirements are about how long something lasts. A light that turns yellow right after it turned green lets no car through, so a traffic light stays green for a minimum time, and the junction gets a fourth requirement: a light that turns green stays green for at least three steps.

The library knows nothing of time yet. A statechart may stay in a state for as long as it likes, or leave it in the very next step. In this lesson we first check the new requirement on the models as they are, then give the library time, and then let the light say "green for at least three steps" as data. The lesson starts from the first state of [Semantic Variants](semantic-variants.md), where one part steps at a time, which can be opened in Live via <LiveLink example={variants}>this link</LiveLink>.

## A requirement over time

A case that checks how long something lasts has to count. It counts on its own, so that it does not rely on the counting it checks:

<LiveEmbed example={step1} show={['NorthGreenForThreeSteps']} />

The case contains the junction, as a system contains its parts, starts it in its `init` and steps it in its own main transition. Before the step, it remembers in a [local variable](../specification/classes-and-behaviour/operations.md#local-variable) whether the north light was green; a local variable lives only in the step that declares it, and may read the state. After the step, an [`if`](../specification/classes-and-behaviour/operations.md#if), which is decided in every step, counts in an integer variable the steps the light stays green, up to three. When the light leaves green before three steps, `leftGreenEarly` becomes true, and the case requires that it never does.

Open <LiveLink example={step1}>this state of the lesson</LiveLink> and verify all. The new case fails, <CaseVerdict example={step1} name="NorthGreenForThreeSteps" />, and its trace shows the north light turning yellow in the step right after it turned green.

## Time in the library

Let us give the library time. A state counts the steps it has been active, and it may say how many steps it lasts at least, before a transition can leave it.

- Give `State` an integer `minimum`, bound to 0, and a variable `time`, which starts at 0.
- Give `State` a transition `passTime`, which adds one to its time while it is active, up to its minimum, and a property `hasLasted`, which tells whether its time has reached the minimum.
- Let a transition fire only once its source has lasted.
- Give `Statechart` a transition `passTime`, which passes time for each of its states, and let `System` pass time for every part in every step, before a part steps.
- Let the green state of the traffic light last at least three steps, by redefining its minimum in a nested block.

The time stops at the minimum, which is all that a transition asks for, and which keeps the number bounded, as a backend such as [Spin](../../backends/spin.mdx) needs.

Verify all. `NorthCanTurnYellow` passes, <CaseVerdict example={step2} name="NorthCanTurnYellow" />, and its trace shows the numbers: in *Steps*, the time of the north light's green state is 0 in the step the light turns green, and 1 and 2 in the two steps after it, and the light turns yellow only in the step that brings it to 3. With the library of the linked state, `NorthGreenForThreeSteps` still fails: <CaseVerdict example={step2} name="NorthGreenForThreeSteps" />. If your library gives another verdict, compare it with that library. This state of the lesson can be opened in Live via <LiveLink example={step2}>this link</LiveLink>.

## Repairing the library

Read the trace of `NorthGreenForThreeSteps`. The first green phase lasts three steps, as it should. Follow the time of the green state through the yellow and the red phase, and into the second green phase. A trace of this case looks like this:

<TraceTimeline rows={[{ name: 'north', lamps: ['Red', 'Green', 'Green', 'Green', 'Yellow', 'Red', 'Green', 'Yellow'] }, { name: 'green\'s time', numbers: [0, 0, 1, 2, 3, 3, 3, 3] }]} broken="NorthGreenForThreeSteps" />

The problem is in the library. Find and fix it!

When it is fixed, every case passes: <CaseVerdict example={final} name="NorthCanTurnGreen" />, <CaseVerdict example={final} name="NeverBothGreen" />, <CaseVerdict example={final} name="NorthGreenOnlyWhileEastRed" />, <CaseVerdict example={final} name="PedestriansCanWalk" />, <CaseVerdict example={final} name="WalkOnlyWhileRed" />, <CaseVerdict example={final} name="NorthCanTurnYellow" /> and <CaseVerdict example={final} name="NorthGreenForThreeSteps" />. The junction and the crossing did not change, and the light said its minimum in one line of data. How would the pedestrian light say that pedestrians walk for at least two steps? The final state of this lesson can be opened in Live via <LiveLink example={final}>this link</LiveLink>.

The next lesson, [Hierarchy and Events](hierarchy-and-events.md), gives the library nested states and events.
