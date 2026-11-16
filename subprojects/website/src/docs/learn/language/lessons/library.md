---
title: 'A Statechart Library'
sidebar_position: 2
description: The second lesson of the Semantifyr Language. Read a small statechart library of states and transitions, write a pedestrian light on it as data, and verify a crossing where pedestrians walk only while the traffic light is red.
---

import LiveEmbed from '@site/src/components/LiveEmbed';
import CaseVerdict from '@site/src/components/CaseVerdict';
import step1 from '@served-examples/oxsts/library-step1';
import step2 from '@served-examples/oxsts/library-step2';
import step3 from '@served-examples/oxsts/library-step3';
import library from '@served-examples/oxsts/library';

# A Statechart Library

**What you verify:** a pedestrian light you write on a library you did not write, and that at a crossing pedestrians walk only while the traffic light is red.

In [The Intersection](intersection.md) a class said everything about how its lights run, and the second light was a copy of the first. A statechart language says once what a state machine is made of, and a machine then lists its states and transitions as data. In the Semantifyr Language such a language is a library of classes, the way Semantifyr writes the semantics of SysML v2 and Gamma. This lesson reads a small one, someone else's, and writes models on it.

## States

A state is an object, and the statechart that owns it holds the active one:

<LiveEmbed example={step1} show={['State', 'Statechart']} />

A class describes objects, its instances. `Statechart` [contains](../specification/classes-and-behaviour/features.md#contains) its states, any number of them, `[0..*]`, each an instance of `State`, and each `State` knows the statechart that contains it through a [`container`](../specification/classes-and-behaviour/features.md#container) feature; [`opposite`](../specification/classes-and-behaviour/features.md#opposite) ties the two directions together, and both sides name each other. `activeState` is a [feature-typed variable](../specification/classes-and-behaviour/variables.md#feature-typed-variables): its values are the states of this statechart, or `nothing` before one is active. A state is active when its statechart holds it, and its transition `activate` makes it so.

## Transitions

A transition is an object too:

<LiveEmbed example={step1} show={['Transition']} />

A `Transition` [refers](../specification/classes-and-behaviour/features.md#refers) to the state it leaves and the state it enters, without owning them. A reference without a multiplicity refers to exactly one, as if written `[1]`. Its main transition fires it: it can be taken only while its source is active, and activates its target. `@Trace` names each transition that fires in the trace's *Calls* view.

## A Light as Data

The traffic light [extends](../specification/classes-and-behaviour/classes.md#inheritance) `Statechart`:

<LiveEmbed example={step1} show={['TrafficLight']} />

Its three states [subset](../specification/classes-and-behaviour/features.md#subsets) the inherited `states`, so they are states of the light, and its three transitions subset `transitions`. The nested block of each transition [redefines](../specification/classes-and-behaviour/features.md#redefine-and-redefines) its two references for this light: `redefine refers from: State[1] = red` keeps the reference's name, type and multiplicity and [binds](../specification/classes-and-behaviour/features.md#feature-binding) it with `=` to the light's `red`, fixed when the model is compiled.

The light starts red in its `init` transition, which runs once, before the first step. Its main transition is a choice of its transitions: in each step one of them fires, through an [inline call](../specification/classes-and-behaviour/inline-operations.md#inline-call) of its main transition.

As in the first lesson, the case extends the light:

<LiveEmbed example={step1} show={['LightCanTurnYellow']} />

Verify it:

<details>
<summary>The verdict Live shows</summary>

- <CaseVerdict example={step1} name="LightCanTurnYellow" />

</details>

In *Steps* the light's `activeState` holds a state, named by its path: `root.red` at the start, then `root.green` and `root.yellow`. *Calls* names the transition that fired in each step.

## A Pedestrian Light

A pedestrian light shows stop or walk. Write it on the library, in the editor you have open, and leave the library as it is:

- A class `PedestrianLight` on the library, with the states `stop` and `walk`, a transition each way, starting at `stop`.
- A case `PedestriansCanWalk` asks whether it can show walk.

<details>
<summary>The verdicts to expect</summary>

- <CaseVerdict example={step2} name="LightCanTurnYellow" />
- <CaseVerdict example={step2} name="PedestriansCanWalk" />

</details>

Which lines of your pedestrian light say how it runs, and which only say what it is?

<details>
<summary>Solution</summary>

<LiveEmbed example={step2} />

</details>

## Two Lights, Two Instances

A class is written once and has as many instances as a model needs. The intersection of the first lesson holds two traffic lights:

<LiveEmbed example={step3} show={['Intersection']} />

`north` and `east` are two instances of `TrafficLight`, each with its own states, transitions and active state. A part's `init` and main transition run only where the whole's call them, so the intersection's `init` starts both lights and its main transition steps one of them. The library's transitions have no guard of their own, so the intersection decides which light may step: a light steps only while the other is red, the guard the first lesson ended with, now on the whole step of a light.

Its cases ask what the first lesson asked:

<LiveEmbed example={step3} show={['EastCanTurnGreen', 'NeverBothGreen', 'NorthGreenOnlyWhileEastRed']} />

<details>
<summary>The verdicts Live shows</summary>

- <CaseVerdict example={step3} name="LightCanTurnYellow" />
- <CaseVerdict example={step3} name="PedestriansCanWalk" />
- <CaseVerdict example={step3} name="EastCanTurnGreen" />
- <CaseVerdict example={step3} name="NeverBothGreen" />
- <CaseVerdict example={step3} name="NorthGreenOnlyWhileEastRed" />

</details>

In the trace of `EastCanTurnGreen`, *Calls* names each transition by its path, `root.east.redToGreen`, and so tells the two lights apart.

## A Crossing

Where pedestrians cross a road, a traffic light and a pedestrian light work together. Build it beside the intersection:

- A class `Crossing` with a traffic light, `light`, and a pedestrian light, `pedestrians`, one of which steps in each step.
- `PedestriansCanWalk` asks its question of the crossing.
- A case `WalkOnlyWhileRed` requires that pedestrians walk only while the traffic light is red.
- Both pass.

<details>
<summary>The verdicts to expect</summary>

- <CaseVerdict example={library} name="LightCanTurnYellow" />
- <CaseVerdict example={library} name="EastCanTurnGreen" />
- <CaseVerdict example={library} name="NeverBothGreen" />
- <CaseVerdict example={library} name="NorthGreenOnlyWhileEastRed" />
- <CaseVerdict example={library} name="PedestriansCanWalk" />
- <CaseVerdict example={library} name="WalkOnlyWhileRed" />

</details>

Take out the guard on the traffic light's step and verify again: which case fails, and what does the last step of its trace show?

<details>
<summary>Solution</summary>

<LiveEmbed example={library} />

</details>

## What You Can Do Now

You can read a library someone else wrote and write models on it as data: classes and their instances, containment with `container` and `opposite`, `subsets`, references and their bindings, feature-typed variables, `init`, and a whole that starts and steps its parts. Each light still says in its own main transition that one of its transitions fires in a step. That is a rule of the statechart language, not of a light, and the next lesson moves it into the library and then changes it: [Changing the Semantics](semantics.md).
