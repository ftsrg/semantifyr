---
title: 'A Statechart Library'
sidebar_position: 4
description: The fourth OXSTS lesson. Write once how a statechart runs, build a traffic light on it, then a pedestrian light, and verify the crossing of the two.
---

import LiveEmbed from '@site/src/components/LiveEmbed';
import CaseVerdict from '@site/src/components/CaseVerdict';
import step1 from '@served-examples/oxsts/library-step1';
import step2 from '@served-examples/oxsts/library-step2';
import library from '@served-examples/oxsts/library';

# A Statechart Library

**What you verify:** that a light built on a library of states still turns yellow, which transition fires in each step, and that pedestrians at a crossing walk only while the traffic light is red.

So far each light spelled out its own cycle in a `choice`. A statechart language says once how any state machine runs, and a machine then only lists its states and transitions. In OXSTS such a language is a library of classes, the way Semantifyr writes the semantics of SysML v2 and Gamma. This lesson builds a small one.

## States

A state is an object, and the statechart that owns it holds the active one:

<LiveEmbed example={step1} show={['State', 'Statechart']} />

`Statechart` [contains](../reference/language/features.md#contains) any number of states, `[0..*]`, and each `State` knows the statechart that contains it through a [`container`](../reference/language/features.md#container) feature; [`opposite`](../reference/language/features.md#opposite) ties the two directions together; both sides name each other, and the validator rejects an `opposite` written on one side only. `activeState` is a [feature-typed variable](../reference/language/variables.md#feature-typed-variables): its values are the states of this statechart, or `nothing` before one is active. When the model is compiled, it becomes an integer that numbers the states. A state is active when its statechart holds it, and `activate` makes it so.

The light [extends](../reference/language/classes.md#inheritance) `Statechart`. Its three states [subset](../reference/language/features.md#subsets) the inherited `states`, so they are states of the light:

<LiveEmbed example={step1} show={['TrafficLight']} />

The light starts red in its `init` transition, which runs once before the first step. Like the main transition, the `init` of a contained part runs only where an `init` calls it, so the case's `init` calls the light's, and its main transition steps the light:

<LiveEmbed example={step1} show={['LightCanTurnYellow']} />

Run it:

<details>
<summary>The verdict Live shows</summary>

- <CaseVerdict example={step1} name="LightCanTurnYellow" />

</details>

In *Steps* the light's `activeState` holds a state now, named by its path: `root.light.red` at the start, then `root.light.green` and `root.light.yellow`.

## Transitions as Data

The light's main transition still spells out its cycle. Make the transitions objects of the library too:

<LiveEmbed example={step2} show={['Transition', 'Statechart']} />

A `Transition` [refers](../reference/language/features.md#refers) to the state it leaves and the state it enters, without owning them, and fires when its source is active. A reference without a multiplicity refers to exactly one, as if written `[1]`. The statechart's main transition is an [`inline for choice`](../reference/language/inline-operations.md#inline-for-choice): when the model is compiled, it becomes a `choice` with one branch for each transition of the statechart.

The light no longer has a main transition. It lists its transitions, and the nested block of each [redefines](../reference/language/features.md#redefine-and-redefines) the transition's two references for this light: `redefine refers from: State[1] = red` keeps the reference's name, type and multiplicity and [binds](../reference/language/features.md#feature-binding) it with `=` to the light's `red`, fixed when the model is compiled:

<LiveEmbed example={step2} show={['TrafficLight']} />

`@Trace` now marks a transition's main transition. Which transition fires in each step?

<details>
<summary>The verdict Live shows</summary>

- <CaseVerdict example={step2} name="LightCanTurnYellow" />

</details>

The *Calls* view of the trace answers: each step names the transition that fired, `root.light.redToGreen` and then `root.light.greenToYellow`. The [inlining page](../how-it-works/inlining.mdx) shows how such calls are expanded.

## A Second Light

The library pays off with a second machine. A pedestrian light has two states and two transitions, and nothing else:

<LiveEmbed example={library} show={['PedestrianLight']} />

At a crossing, the traffic light switches only while the pedestrians wait, and the pedestrian light only while the traffic light is red:

<LiveEmbed example={library} show={['Crossing']} />

Two new cases ask whether pedestrians can walk at all, and whether they walk only while the traffic light is red:

<LiveEmbed example={library} show={['PedestriansCanWalk', 'WalkOnlyWhileRed']} />

Run the model:

<details>
<summary>The verdicts Live shows</summary>

- <CaseVerdict example={library} name="LightCanTurnYellow" />
- <CaseVerdict example={library} name="PedestriansCanWalk" />
- <CaseVerdict example={library} name="WalkOnlyWhileRed" />

</details>

The whole model:

<LiveEmbed example={library} />

## What You Can Do Now

You can write the meaning of a small language once, as classes, and describe machines in it as data: inheritance, containment with `container` and `opposite`, `subsets`, references, feature-typed variables, `inline for choice` and nested bindings. The last lesson makes the library richer, with nested states, entry actions and events: [Hierarchy, Events and the Environment](hierarchy.md).
