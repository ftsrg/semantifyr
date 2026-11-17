---
title: 'Semantic Libraries'
sidebar_position: 2
description: The second lesson of the Semantifyr Language. Write the traffic lights and a pedestrian light as data on a small statechart library that says once how a statechart runs, add a crosswalk to the junction, and find what the obvious guard leaves open.
---

import LiveEmbed from '@site/src/components/LiveEmbed';
import LiveLink from '@site/src/components/LiveLink';
import CaseVerdict from '@site/src/components/CaseVerdict';
import { Junction, LibraryClasses, LightAsData, TraceTimeline } from '@site/src/components/LessonFigures';
import step1 from '@served-examples/oxsts/semantic-libraries-step1';
import step2 from '@served-examples/oxsts/semantic-libraries-step2';
import step3 from '@served-examples/oxsts/semantic-libraries-step3';
import step4 from '@served-examples/oxsts/semantic-libraries-step4';
import final from '@served-examples/oxsts/semantic-libraries';

# Semantic Libraries

The goal of this lesson is to write models on a library of classes that says once how a statechart runs, so that each light of the running example is only data: its states, its transitions and the state it starts in.

## Running example

In [Modeling and Verification](modeling-and-verification.md) the light was drawn as a state diagram, but the model said something else: a choice of assumptions over a color, written out once more for each light of the junction.

Why would we want to use a library? For reusability. What a state machine does when it runs is the same for every light: a transition fires while the state it leaves is active and its guards hold. Written once, as classes, it is reused by the two lights of the junction, by a pedestrian light, and by any light that comes later, and each of them only says what it is.

This lesson adds a crosswalk to the junction. It crosses the east road in front of the east light, and the pedestrians have a light of their own, which shows stop or walk, and a button they press when they want to cross.

<Junction crosswalk />

The junction keeps the requirements of the first lesson and gets two more:

- Pedestrians walk only while the east light is red.
- Pedestrians can walk.

## A statechart as objects

A library is a handful of ordinary classes. In ours, a state is an object, and a statechart is an object that contains its states and holds the active one:

<LiveEmbed example={step1} show={['State', 'Statechart']} />

A `Statechart` [contains](../specification/classes-and-behaviour/features.md#contains) any number of states, `[0..*]`, and each `State` knows the statechart that contains it through its [`container`](../specification/classes-and-behaviour/features.md#container) feature; [`opposite`](../specification/classes-and-behaviour/features.md#opposite) ties the two directions together. The type of `activeState` is a feature, `states`, so the variable holds one of this statechart's states, or `nothing` ([feature-typed variables](../specification/classes-and-behaviour/variables.md#feature-typed-variables)). A state compares itself with it to say whether it is active, and activating a state assigns it. The statechart's `init` activates its initial state once, before the first step, and in every step [`inline for choice`](../specification/classes-and-behaviour/inline-operations.md#inline-for-choice) fires one of its transitions, whichever can fire, unless the statechart stays as it is.

A transition is an object too:

<LiveEmbed example={step1} show={['Transition']} />

It [refers](../specification/classes-and-behaviour/features.md#refers) to the state it leaves and the state it enters, without owning them. Its main transition can be taken only while its source is active, and then activates its target; `inline` puts the body of the called transition in its place. `@Trace` makes the *Calls* view of a trace list each transition that fires.

With these three classes, a traffic light is data:

<LiveEmbed example={step1} show={['TrafficLight', 'LightCanTurnGreen']} />

The light [extends](../specification/classes-and-behaviour/classes.md#inheritance) `Statechart`. Its three states [subset](../specification/classes-and-behaviour/features.md#subsets) the inherited `states`, so they are states of the light, and its three transitions subset `transitions`. The nested block of a transition [redefines](../specification/classes-and-behaviour/features.md#redefine-and-redefines) the references it inherits: `redefine refers from: State = red` [binds](../specification/classes-and-behaviour/features.md#feature-binding) the source of this transition to the light's red state when the model is compiled. The light has no sensor yet, so it turns green whenever it likes.

Press **Open** on the block and verify all: `LightCanTurnGreen` passes, <CaseVerdict example={step1} name="LightCanTurnGreen" />, and the *Calls* view of its trace shows `redToGreen` firing.

The pedestrian light is the first light to reuse the library.

- Write a class `PedestrianLight` on the library. It shows stop or walk, starts at stop, and can switch between the two.
- Add the case `PedestriansCanWalk`, which asks whether it can show walk.

The case passes, <CaseVerdict example={step2} name="PedestriansCanWalk" />, and your pedestrian light says nothing about how it runs. Compare it with <LiveLink example={step2}>one solution</LiveLink>.

## Guards

A traffic light of the running example turns green only for a waiting car, and only while the crossing traffic is stopped. Such conditions are the guards of a transition. A guard is either a sensor, set by the environment as the car sensor of the first lesson was, or a state of another statechart, which holds while that state is active:

<LiveEmbed example={step3} show={['Condition', 'Sensor', 'State']} />

`Condition` is an [abstract class](../specification/classes-and-behaviour/classes.md#abstract-classes): it says that a condition can be active and leaves how to its subclasses. A transition refers to any number of guards, and [`inline for`](../specification/classes-and-behaviour/inline-operations.md#inline-for-sequence) assumes each of them before the transition fires:

<LiveEmbed example={step3} show={['Transition']} />

<LibraryClasses />

The traffic light refers to its sensor and to the state in which the crossing traffic is stopped, and its transition to green declares both as guards. Which sensor and which state those are, the light leaves open:

<LiveEmbed example={step3} show={['TrafficLight']} />

<LightAsData />

The junction holds two traffic lights and their sensors, and fills in what each light left open, the north light waiting for the east light's red state and the east light for the north light's:

<LiveEmbed example={step3} show={['Junction']} />

The north and the east light are two instances of `TrafficLight`, each with its own states, transitions and active state. A light contained in the junction does nothing unless the junction calls it: the junction's `init` starts both lights, and its main transition lets the environment set both sensors and then steps one of the lights.

Open <LiveLink example={step3}>the junction on the library</LiveLink> and verify all. The requirements of the first lesson hold as they did on its model: <CaseVerdict example={step3} name="NorthCanTurnGreen" />, <CaseVerdict example={step3} name="NeverBothGreen" /> and <CaseVerdict example={step3} name="NorthGreenOnlyWhileEastRed" />. In the *Calls* view of `NorthCanTurnGreen`, a transition is named by its path, such as `root.north.redToGreen`, which tells the two lights apart.

## The crosswalk

Now let us add the crosswalk of the running example to the junction, without changing the library.

- The pedestrian light turns to walk only while its button is pressed and the east light is red.
- The junction contains the pedestrian light and its button, which the environment presses or releases in every step, as it sets the sensors.
- `PedestriansCanWalk` becomes a case of the junction, and the case `WalkOnlyWhileEastRed` requires that pedestrians walk only while the east light is red.

Before you verify, decide whether `WalkOnlyWhileEastRed` holds. The pedestrians can walk, <CaseVerdict example={step4} name="PedestriansCanWalk" />, but with the crosswalk of <LiveLink example={step4}>this state of the junction</LiveLink> the requirement fails: <CaseVerdict example={step4} name="WalkOnlyWhileEastRed" />. Read its trace in Live. When do the pedestrians start to walk, and what does the east light do after that?

The problem is in the model, and the library can stay as it is. Find and fix it!

When it is fixed, every case of the junction passes: <CaseVerdict example={final} name="NorthCanTurnGreen" />, <CaseVerdict example={final} name="NeverBothGreen" />, <CaseVerdict example={final} name="NorthGreenOnlyWhileEastRed" />, <CaseVerdict example={final} name="PedestriansCanWalk" /> and <CaseVerdict example={final} name="WalkOnlyWhileEastRed" />. A guard is checked only when its transition fires. The pedestrians started on red, and nothing kept the east light red after that, since it waited only for the north light:

<TraceTimeline rows={[{ name: 'north', lamps: ['Red', 'Red', 'Red'] }, { name: 'east', lamps: ['Red', 'Red', 'Green'] }, { name: 'pedestrians', lamps: ['Stop', 'Walk', 'Walk'] }, { name: 'button', flags: [false, true, false] }, { name: 'east car', flags: [false, false, true] }]} broken="WalkOnlyWhileEastRed" />

The east light has two things to wait for now, and the junction says so in its data. Compare your repair with <LiveLink example={final}>the final state of this lesson</LiveLink>.

The lights are data now, and one rule is still written in the model: how the junction steps its lights. The next lesson, [Semantic Variants](semantic-variants.md), moves that rule into the library too, and changes it.
