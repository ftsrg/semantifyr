---
title: 'Semantic Libraries'
sidebar_position: 2
description: The second lesson of the Semantifyr Language. Read a small statechart library that says once how a statechart runs, write the traffic lights and a pedestrian light on it as data, and check a pedestrian crossing.
---

import LiveEmbed from '@site/src/components/LiveEmbed';
import LiveLink from '@site/src/components/LiveLink';
import CaseVerdict from '@site/src/components/CaseVerdict';
import { Crossing, LibraryClasses, LightAsData, TraceTimeline } from '@site/src/components/LessonFigures';
import step1 from '@served-examples/oxsts/semantic-libraries-step1';
import step2 from '@served-examples/oxsts/semantic-libraries-step2';
import step3 from '@served-examples/oxsts/semantic-libraries-step3';
import final from '@served-examples/oxsts/semantic-libraries';

# Semantic Libraries

The goal of this lesson is to write models on a library of classes that says once how a statechart runs, so that each light of the running example is only data: its states, its transitions and the state it starts in.

## Running example

In [Modeling and Verification](modeling-and-verification.md) the light was drawn as a state diagram, but the model said something else: a choice of assumptions over a color, written out once more for each light of the junction.

Why would we want to use a library? For reusability. What a state machine does when it runs, that one of its transitions fires while its source state is active and its guards hold, is the same for every light. Written once, as classes, it is reused by the two lights of the junction, by a pedestrian light, and by any light that comes later, and each of them only says what it is.

This lesson adds a pedestrian crossing to the running example. A road is crossed by a crosswalk. The cars have a traffic light with the cycle and the sensor of the junction's lights, and the pedestrians have a light of their own, which shows stop or walk and turns to walk when someone presses its button.

<Crossing />

The crossing must meet the following requirements:

- Pedestrians walk only while the traffic light is red.
- Pedestrians can walk.

The junction keeps the requirements of the first lesson.

## The library

The library is a handful of classes. A state is an object, and the statechart that contains it holds the active one:

<LiveEmbed example={step1} show={['State', 'Statechart']} />

A class describes objects, its instances. `Statechart` [contains](../specification/classes-and-behaviour/features.md#contains) its states, any number of them, `[0..*]`, and each `State` knows the statechart that contains it through a [`container`](../specification/classes-and-behaviour/features.md#container) feature; [`opposite`](../specification/classes-and-behaviour/features.md#opposite) ties the two directions together. `activeState` is a [feature-typed variable](../specification/classes-and-behaviour/variables.md#feature-typed-variables): its values are the states of this statechart, or `nothing`. A statechart starts in its initial state, which its `init` transition activates once, before the first step. In every step it fires one of its transitions, whichever can fire, by [`inline for choice`](../specification/classes-and-behaviour/inline-operations.md#inline-for-choice), or it stays as it is, as the light of the first lesson could.

A transition is an object too:

<LiveEmbed example={step1} show={['Transition']} />

A transition [refers](../specification/classes-and-behaviour/features.md#refers) to the state it leaves and the state it enters, without owning them, and to its guards, the conditions under which it may fire. Its main transition fires it: it can be taken only while its source is active and, by [`inline for`](../specification/classes-and-behaviour/inline-operations.md#inline-for-sequence), while each of its guards holds, and then it activates its target. `@Trace` makes the trace's *Calls* view list each transition that fires.

A guard is a condition, and the library knows two kinds of them:

<LiveEmbed example={step1} show={['Condition', 'Sensor']} />

`Condition` is an [abstract class](../specification/classes-and-behaviour/classes.md#abstract-classes): it says that a condition can be active, and leaves how to its subclasses. A state is a condition, active while its statechart holds it, and so is a sensor, active while it detects something. A sensor is set by the environment, as the car sensor of the first lesson was.

<LibraryClasses />

## A light as data

The traffic light [extends](../specification/classes-and-behaviour/classes.md#inheritance) `Statechart`:

<LiveEmbed example={step1} show={['TrafficLight']} />

Its three states [subset](../specification/classes-and-behaviour/features.md#subsets) the inherited `states`, so they are states of the light, and its three transitions subset `transitions`. The nested block of each transition [redefines](../specification/classes-and-behaviour/features.md#redefine-and-redefines) the references it inherits for this light: `redefine refers from: State = red` [binds](../specification/classes-and-behaviour/features.md#feature-binding) the reference to the light's red state when the model is compiled. The transition to green declares two guards in its block, the light's sensor and the state in which the crossing traffic is stopped, and which sensor and which state those are, the light leaves open. Together this is the cycle of the running example as data:

<LightAsData />

No line of the light says how it runs.

## The junction

A class can have as many instances as a model needs. The junction holds two traffic lights and their sensors:

<LiveEmbed example={step1} show={['Junction']} />

The north and the east light are two instances of `TrafficLight`, each with its own states, transitions and active state. The nested block of each binds what the light left open: its sensor, and the red state of the other light, which it waits for. The `init` and the main transition of a part run only where the whole calls them, so the junction's `init` starts both lights, and its main transition lets the environment set both sensors and then steps one of the lights.

Its cases are the requirements of the first lesson:

<LiveEmbed example={step1} show={['NorthCanTurnGreen', 'NeverBothGreen', 'NorthGreenOnlyWhileEastRed']} />

Open <LiveLink example={step1}>this state of the lesson</LiveLink> and verify all. The three requirements hold on the library as they held on the model of the first lesson: <CaseVerdict example={step1} name="NorthCanTurnGreen" />, <CaseVerdict example={step1} name="NeverBothGreen" /> and <CaseVerdict example={step1} name="NorthGreenOnlyWhileEastRed" />. In the *Calls* view of `NorthCanTurnGreen`, a transition is named by its path, such as `root.north.redToGreen`, which tells the two lights apart, and in *Steps* the active state of each light is named the same way.

## Reusing the library

Now let us build the crossing of the running example beside the junction, without changing the library.

- Add a class `PedestrianLight` on the library, with the states `stop` and `walk`, starting at stop. It refers to a sensor, its button, and turns to walk while the button is pressed. It turns back to stop at any time.
- Add a class `Crossing` with a sensor for the cars, a push button, a traffic light and a pedestrian light, which steps one of its lights in each step as the junction does.
- Add the case `PedestriansCanWalk`, which asks whether the pedestrian light can show walk.

It passes: <CaseVerdict example={step2} name="PedestriansCanWalk" />. This state of the lesson can be opened in Live via <LiveLink example={step2}>this link</LiveLink>.

Now add the case `WalkOnlyWhileRed`, which requires that pedestrians walk only while the traffic light is red, and decide its verdict before you verify. With the pedestrian light of the linked state it fails, <CaseVerdict example={step3} name="WalkOnlyWhileRed" />, and a trace of it looks like this:

<TraceTimeline rows={[{ name: 'light', lamps: ['Red', 'Green', 'Green'] }, { name: 'pedestrians', lamps: ['Stop', 'Stop', 'Walk'] }, { name: 'car', flags: [false, true, true] }, { name: 'button', flags: [false, false, true] }]} broken="WalkOnlyWhileRed" />

This state of the lesson can be opened in Live via <LiveLink example={step3}>this link</LiveLink>. The traffic light of the crossing needed no new line: it already waits for the crossing traffic to stop, and the crossing only told it which state that is. Give the pedestrian light the same kind of guard, so that it turns to walk only while the traffic light is red.

Then both cases of the crossing pass, <CaseVerdict example={final} name="PedestriansCanWalk" /> and <CaseVerdict example={final} name="WalkOnlyWhileRed" />, and the junction's still do. Which lines of your pedestrian light say how it runs? The final state of this lesson can be opened in Live via <LiveLink example={final}>this link</LiveLink>.

The models now say only what the lights are, and one rule is still written in them: how the junction and the crossing step their lights. The next lesson, [Semantic Variants](semantic-variants.md), moves that rule into the library too, and changes it.
