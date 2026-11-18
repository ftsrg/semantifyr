---
title: 'Extending the Library'
sidebar_position: 4
description: The fourth lesson of the Semantifyr Language. Extend the statechart library with a new concept, time, so that a light says as data how long it stays green, and grow it further with nested states and events.
---

import LiveEmbed from '@site/src/components/LiveEmbed';
import LiveLink from '@site/src/components/LiveLink';
import CaseVerdict from '@site/src/components/CaseVerdict';
import { NestedStates, TraceTimeline } from '@site/src/components/LessonFigures';
import variants from '@lesson-stages/semantic-variants-step1';
import step1 from '@lesson-stages/extending-the-library-step1';
import step2 from '@lesson-stages/extending-the-library-step2';
import final from '@lesson-stages/extending-the-library';
import nestedStatesStep1 from '@lesson-stages/extending-the-library-nested-states-step1';
import nestedStatesStep2 from '@lesson-stages/extending-the-library-nested-states-step2';
import nestedStates from '@lesson-stages/extending-the-library-nested-states';
import events from '@lesson-stages/extending-the-library-events';

# Extending the Library

The goal of this lesson is to extend the statechart library with a new concept, time, so that a model can say as data how long a light stays in a state.

## Running example

Why would we count steps? Because real requirements are about how long something lasts. A light that turns yellow right after it turned green lets no car through, so a traffic light stays green for a minimum time, and the junction gets a new requirement: a light that turns green stays green for at least three steps.

The library knows nothing of time yet. A statechart may stay in a state for as long as it likes, or leave it in the very next step. In this lesson we first check the new requirement on the junction as it is, then give the library time, and then let the light say "green for at least three steps" as data. We start from the library of [Semantic Variants](semantic-variants.md) with its first rule, one part in a step, <LiveLink example={variants}>as it was before the rule changed</LiveLink>.

## Requirements over time

A case that checks how long something lasts has to count. It does its own counting, so that it does not rely on the counting it checks:

<LiveEmbed example={step1} show={['NorthGreenForThreeSteps']} />

The case contains the junction, as a system contains its parts. It starts the junction in its `init` and steps it in its own main transition. A feature can hold a number, not only an instance. Here `required` is bound to 3, and a subclass or a nested block could redefine it, as it redefines a reference. Before the step, the case remembers in a [local variable](../specification/classes-and-behaviour/operations.md#local-variable) whether the north light was green. A local variable lives only in the step that declares it, and it may read the state. After the step, an [`if`](../specification/classes-and-behaviour/operations.md#if) counts the steps the light stays green in an integer variable, up to the required number. The condition of an `if` is checked in every step, while the model runs. When the light leaves green before that, `leftGreenEarly` becomes true, and the case requires that it never does.

Open <LiveLink example={step1}>the junction with this case</LiveLink> and verify all. The new case fails, <CaseVerdict example={step1} name="NorthGreenForThreeSteps" />, and its trace shows the north light turning yellow in the step right after it turned green.

## Time in the library

Let us give the library time, so that a light can say how long it stays in a state.

- A state can say how many steps it lasts at least before a transition leaves it. A state that says nothing can be left at once, as now.
- Time passes in every step, for every state of every part, before a part steps.
- The count stays bounded. No transition asks for more than the minimum, so the count can stop there. Spin can check only numbers that stay within bounds.
- The green state of the traffic light lasts at least three steps, and the light says so in its data.

A minimum that never ends would also meet the requirement: a light that never leaves green never leaves it early. Add the case `NorthCanTurnYellow`, which asks whether the north light can still turn yellow, and verify all. It passes, <CaseVerdict example={step2} name="NorthCanTurnYellow" />. In the *Steps* view of its trace, follow the light through its first green phase: it turns yellow only after green has lasted three steps.

`NorthGreenForThreeSteps` still fails with the library of <LiveLink example={step2}>this state of the lesson</LiveLink>: <CaseVerdict example={step2} name="NorthGreenForThreeSteps" />. If yours passes, compare your library with that one. Read the trace in Live: the first green phase lasts three steps, as it should. What happens in the second?

## Fixing the library

The problem is in the library. Find and fix it!

When it is fixed, every case passes: <CaseVerdict example={final} name="NorthCanTurnGreen" />, <CaseVerdict example={final} name="NeverBothGreen" />, <CaseVerdict example={final} name="NorthGreenOnlyWhileEastRed" />, <CaseVerdict example={final} name="PedestriansCanWalk" />, <CaseVerdict example={final} name="WalkOnlyWhileEastRed" />, <CaseVerdict example={final} name="NorthGreenForThreeSteps" /> and <CaseVerdict example={final} name="NorthCanTurnYellow" />. One way to write the library with time is <LiveLink example={final}>the final state of this lesson</LiveLink>.

A state's time belongs to one stay in the state. Before the fix, the green state kept the time it had counted. So when the light came back to green, the state had already lasted its minimum:

<TraceTimeline rows={[{ name: 'north', lamps: ['Red', 'Green', 'Green', 'Green', 'Yellow', 'Red', 'Green', 'Yellow'] }, { name: 'green\'s time', numbers: [0, 0, 1, 2, 3, 3, 3, 3] }]} broken="NorthGreenForThreeSteps" />

The junction did not change, and the light said its minimum in one line of data. How would the pedestrian light say that pedestrians walk for at least two steps?

## Optional tasks

The statechart languages that engineers use have more than states, transitions and time. Two more extensions follow, each a task of its own with one solution to compare with. Nested states start from the final state of this lesson, and events from the solution of nested states.

### Nested states

At night or in maintenance, a traffic light leaves its cycle for a standby mode, in which it flashes yellow, and when it comes back it starts again from red. It can go to standby from any color. With flat states, each color would need a transition of its own to standby. With nested states, the colors are substates of an operating state, and one transition leaves operating, whichever color is active.

<NestedStates />

Grow the library and the light in four steps, and verify after each of them:

- A state can contain states, as a statechart does, and a statechart can serve as a guard. Change only the library: every case keeps its verdict.
- A state without substates has no initial state. When a state that has one is entered, its initial state is entered too, and time passes for substates as well: every case keeps its verdict.
- The colors of the traffic light become substates of a state `operating`, which starts at red, and the light starts in operating. Nothing new happens yet, but every path that names a color changes: the north light's red is now `north.operating.red`, in the light's own transitions, in the states the junction gives the lights to wait for, in the pedestrians' guard and in every case. Every case keeps its verdict.
- A state `standby` lies beside operating, and the light goes from operating to standby and back at any time. The case `NorthCanStandBy` asks whether the north light can be in standby, and `NorthNeverGreenInStandby` requires that it is never in standby and green at the same time.

After the third step, your model can be compared with <LiveLink example={nestedStatesStep1}>this state of the lesson</LiveLink>. After the fourth, the north light can go to standby, <CaseVerdict example={nestedStatesStep2} name="NorthCanStandBy" />, but two requirements break on <LiveLink example={nestedStatesStep2}>the state with standby</LiveLink>: <CaseVerdict example={nestedStatesStep2} name="NorthNeverGreenInStandby" /> and <CaseVerdict example={nestedStatesStep2} name="NorthGreenForThreeSteps" />.

Start from the state with standby, so that your library is the one this page speaks of, and make every requirement hold. There is a flaw in the library, and once it is repaired, a flaw in the light shows. `NorthGreenForThreeSteps` counts a light that goes to standby while it is green as leaving green early: decide whether the light or the requirement is wrong. Compare with <LiveLink example={nestedStates}>one solution</LiveLink>.

### Events

So far a light goes to standby and back at any time. An operator should decide, with two events, pause and resume. A sensor says how things are, for as long as they stay so. The operator's pause happens at one moment, and the light should react to it once. This task starts from <LiveLink example={nestedStates}>the solution of nested states</LiveLink>.

- An event belongs to a statechart. A statechart has at most one current event in a step, as it has one active state. An event is a condition that is active while it is the current one, so a transition takes it as a guard like any other.
- In every step, before time passes, every part gets any one of its events, or none. [`havoc`](../specification/classes-and-behaviour/operations.md#havoc) on a variable that holds an instance chooses any instance it can hold, or nothing.
- The traffic light goes to standby only on pause, and back only on resume.

Every case keeps its verdict, and in the trace of `NorthCanStandBy` the north light's event is pause in the step it goes to standby. Compare with <LiveLink example={events}>one solution</LiveLink>.

## The libraries of Gamma and SysML v2

You have built a small statechart language in the Semantifyr Language: states, transitions and guards, how the parts of a system step, time, and with the optional tasks nested states and events. Each is a few classes of a library, and the library's rules decide the verdicts of models that never changed. The libraries that give Gamma and SysML v2 their meaning are built from the same patterns. Gamma's library also has orthogonal regions, entry and exit actions, history and priorities. Every construct of the language is in the [specification](../specification/index.md), and [How a Semantifyr Model Compiles](../compilation/index.mdx) shows how a model becomes what a backend checks.
