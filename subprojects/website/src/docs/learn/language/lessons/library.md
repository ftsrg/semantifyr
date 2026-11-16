---
title: 'A Statechart Library'
sidebar_position: 2
description: The second lesson of the Semantifyr Language. Read a small statechart library of states and transitions, write traffic lights and a pedestrian light on it as data, and check a pedestrian crossing.
---

import LiveEmbed from '@site/src/components/LiveEmbed';
import LiveLink from '@site/src/components/LiveLink';
import CaseVerdict from '@site/src/components/CaseVerdict';
import { Crossing, LibraryClasses, LightAsData } from '@site/src/components/LessonFigures';
import step1 from '@served-examples/oxsts/library-step1';
import step2 from '@served-examples/oxsts/library-step2';
import step3 from '@served-examples/oxsts/library-step3';
import library from '@served-examples/oxsts/library';

# A Statechart Library

The goal of this lesson is to write models on a library of classes that someone else wrote, as a statechart language does it: the library says once what a state machine is made of, and each machine is data for it.

## Running example

In [The Intersection](intersection.md) the cycle of the light was written out by hand in the light's main transition, and the junction wrote it out once more for each of its lights. Every new kind of light would need another copy.

This lesson adds a pedestrian crossing to the running example. A road is crossed by a crosswalk. The cars have a traffic light, `light`, with the cycle of the first lesson, and the pedestrians have a light of their own, `pedestrians`, which shows stop or walk.

<Crossing />

The crossing must meet the following requirements:

- Pedestrians walk only while the traffic light is red.
- Pedestrians can walk at all.

The two lights are two state machines with different states. Instead of writing out a third cycle, we write both lights on a small statechart library, and keep the junction of the first lesson on it as well.

## Reading the library

The library has three classes. A state is an object, and the statechart that owns it holds the active one:

<LiveEmbed example={step1} show={['State', 'Statechart']} />

A class describes objects, its instances. `Statechart` [contains](../specification/classes-and-behaviour/features.md#contains) its states, any number of them, `[0..*]`, and each `State` knows the statechart that contains it through a [`container`](../specification/classes-and-behaviour/features.md#container) feature; [`opposite`](../specification/classes-and-behaviour/features.md#opposite) ties the two directions together. `activeState` is a [feature-typed variable](../specification/classes-and-behaviour/variables.md#feature-typed-variables): its values are the states of this statechart, or `nothing` before one is active. A state is active while its statechart holds it, and its transition `activate` makes it so.

A transition is an object too:

<LiveEmbed example={step1} show={['Transition']} />

A `Transition` [refers](../specification/classes-and-behaviour/features.md#refers) to the state it leaves and the state it enters, without owning them. A reference without a multiplicity refers to exactly one. The main transition of a `Transition` fires it: it can be taken only while its source is active, and it activates its target. `@Trace` makes the trace's *Calls* view list each transition that fires.

<LibraryClasses />

## Modeling a light on the library

The traffic light [extends](../specification/classes-and-behaviour/classes.md#inheritance) `Statechart`, and lists its states and transitions:

<LiveEmbed example={step1} show={['TrafficLight']} />

Its three states [subset](../specification/classes-and-behaviour/features.md#subsets) the inherited `states`, so they are states of the light, and its three transitions subset `transitions`. The nested block of each transition [redefines](../specification/classes-and-behaviour/features.md#redefine-and-redefines) its two references for this light: `redefine refers from: State[1] = red` [binds](../specification/classes-and-behaviour/features.md#feature-binding) the reference to the light's `red` when the model is compiled. Together they are the cycle of the running example as data:

<LightAsData />

The light starts red in its `init` transition, which runs once, before the first step. Its main transition is a choice of its transitions; in each step one of them fires through an [inline call](../specification/classes-and-behaviour/inline-operations.md#inline-call) of its main transition.

The case extends the light, as in the first lesson:

<LiveEmbed example={step1} show={['LightCanTurnYellow']} />

- Press **Open** on the block above and verify the case. It passes: <CaseVerdict example={step1} name="LightCanTurnYellow" />.
- Observe in *Steps* that `activeState` holds a state, named by its path: `root.red` at the start, then `root.green` and `root.yellow`.
- Observe in *Calls* which transition fired in each step.

## Modeling the pedestrian light

Now let us write the pedestrian light on the same library, without changing the library.

- Add a class `PedestrianLight` on the library, with the states `stop` and `walk`, a transition in each direction, starting at `stop`.
- Add the case `PedestriansCanWalk`, which asks whether the pedestrian light can show walk.
- Verify all. Both cases pass: <CaseVerdict example={step2} name="LightCanTurnYellow" /> and <CaseVerdict example={step2} name="PedestriansCanWalk" />.
- Which lines of your pedestrian light say how it runs, and which only say what it is?
- This state of the lesson can be opened via <LiveLink example={step2}>this link</LiveLink>.

## Composing two lights

A class can have as many instances as a model needs. The junction of the first lesson holds two traffic lights:

<LiveEmbed example={step3} show={['Intersection']} />

`north` and `east` are two instances of `TrafficLight`, each with its own states, transitions and active state. The `init` and main transition of a part run only where the whole calls them, so the junction's `init` starts both lights, and its main transition steps one of them. The library's transitions have no guards, so the junction decides which light may step: a light steps only while the other one is red, which is the guard the first lesson ended with.

Its cases are the requirements of the first lesson:

<LiveEmbed example={step3} show={['EastCanTurnGreen', 'NeverBothGreen', 'NorthGreenOnlyWhileEastRed']} />

- Open <LiveLink example={step3}>this state of the lesson</LiveLink> and verify all. The three requirements hold on the library as well: <CaseVerdict example={step3} name="EastCanTurnGreen" />, <CaseVerdict example={step3} name="NeverBothGreen" /> and <CaseVerdict example={step3} name="NorthGreenOnlyWhileEastRed" />.
- Observe in the *Calls* view of `EastCanTurnGreen` that a transition is named by its path, such as `root.east.redToGreen`, which tells the two lights apart.

## Modeling the crossing

Now let us build the crossing of the running example beside the junction.

- Add a class `Crossing` with a traffic light, `light`, and a pedestrian light, `pedestrians`. In each step one of them steps.
- Change `PedestriansCanWalk` so that it asks its question of the crossing.
- Add the case `WalkOnlyWhileRed`, which requires that pedestrians walk only while the traffic light is red.
- Guard the steps of the two lights so that both requirements hold. Verify all: <CaseVerdict example={library} name="PedestriansCanWalk" /> and <CaseVerdict example={library} name="WalkOnlyWhileRed" />.
- Remove the guard on the step of the traffic light and verify again. Which case fails, and what does the last step of its trace show? Put the guard back.
- The final state of this lesson can be opened via <LiveLink example={library}>this link</LiveLink>.

Each light still says in its own main transition that one of its transitions fires in a step. The next lesson moves that rule into the library, and then changes it.
