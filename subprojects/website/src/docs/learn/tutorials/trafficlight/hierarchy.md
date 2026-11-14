---
title: 'Hierarchy, Events and the Environment'
sidebar_position: 5
description: The fifth OXSTS lesson. Grow the statechart library with nested states, entry actions and events, let an environment send them, and override the lights in an emergency.
---

import LiveEmbed from '@site/src/components/LiveEmbed';
import CaseVerdict from '@site/src/components/CaseVerdict';
import step1 from '@served-examples/oxsts/advanced-step1';
import step2 from '@served-examples/oxsts/advanced-step2';
import step3 from '@served-examples/oxsts/advanced-step3';
import step4 from '@served-examples/oxsts/advanced-step4';
import step5 from '@served-examples/oxsts/advanced-step5';
import advanced from '@served-examples/oxsts/advanced';

# Hierarchy, Events and the Environment

**What you verify:** that a light is never green while in standby, that its lamp shows green only in the green state, that it stays out of standby while no operator asks for it, and that an emergency turns both lights of an intersection red.

This lesson grows the library of [A Statechart Library](library.md) with the patterns real semantic libraries are built from. The intersection of [The Intersection](intersection.md) comes back on it, with its guard on red, and the cases of each step keep running against the steps after it.

## Nested States

At night or in maintenance, a traffic light leaves its cycle for a standby mode, and it starts again from red when it comes back. The colors become substates of an `operating` state, beside a `standby` state.

A nested state owns states of its own, holds the active one and is a state of its parent. `Statechart` already does the first two, so `State` extends `Statechart`:

<LiveEmbed example={step1} show={['State']} />

`initial` names the substate entered when the state is entered; `[0..1]` lets a state without substates leave it as `nothing`. `activate` activates the state, then its initial substate. An [`inline if`](../../language/inline-operations.md#inline-if) is decided when the model is compiled, like `inline for`: for a state without an initial substate, the block is gone.

<LiveEmbed example={step1} show={['TrafficLight']} />

The nested block of `operating` declares the colors as its substates and binds its `initial` to `red`. The transitions stay in the light and reach a color through `operating`. A new case asks that the light is never green while in standby:

<LiveEmbed example={step1} show={['NeverGreenInStandby']} />

Predict, then run the step:

<details>
<summary>The verdicts Live shows</summary>

- <CaseVerdict example={step1} name="LightCanTurnGreen" />
- <CaseVerdict example={step1} name="NeverBothGreen" />
- <CaseVerdict example={step1} name="NeverGreenInStandby" />

</details>

Open the trace of `NeverGreenInStandby` and read *Steps*: the light's `activeState` is `standby`, and `operating`'s `activeState` keeps moving through the colors after it.

## Parents and Children

A state asks only its parent whether it is active, and `operating` keeps its last color after the light has left it, so that color still counts as active and its transitions keep firing in standby. A substate is active only while its parent is:

<LiveEmbed example={step2} show={['State', 'Statechart']} />

The light belongs to no state; while it runs, it is active, so `Statechart` says `true`. `State` [redefines](../../language/properties.md#redefining-properties) the property: active when its parent holds it and the parent is active. The calls walk up the hierarchy to the light, resolved for each state when the model is compiled.

<details>
<summary>The verdicts Live shows</summary>

- <CaseVerdict example={step2} name="LightCanTurnGreen" />
- <CaseVerdict example={step2} name="NeverBothGreen" />
- <CaseVerdict example={step2} name="NeverGreenInStandby" />

</details>

## Entry Actions

An entry action runs whenever a state becomes active, whichever transition enters it. `State` gets an empty `entry` transition, which `activate` calls after the state has become active and before its initial substate is entered:

<LiveEmbed example={step3} show={['State']} />

A nested block holds features only, so behavior that differs between states goes into a subclass of `State`, and the data that differs between its instances into their nested blocks. The light gets a lamp, and a `LampState` switches it to its color on entry:

<LiveEmbed example={step3} show={['LightColor', 'Lamp', 'LampState']} />

<LiveEmbed example={step3} show={['TrafficLight']} />

Each color is now a `LampState` bound to the light's lamp and its color. A new case asks that the lamp shows green only while the green state is active:

<LiveEmbed example={step3} show={['GreenLampOnlyInGreen']} />

<details>
<summary>The verdicts Live shows</summary>

- <CaseVerdict example={step3} name="LightCanTurnGreen" />
- <CaseVerdict example={step3} name="NeverBothGreen" />
- <CaseVerdict example={step3} name="NeverGreenInStandby" />
- <CaseVerdict example={step3} name="GreenLampOnlyInGreen" />

</details>

Read the trace of `GreenLampOnlyInGreen`: the light turns green, then enters standby, and the lamp still shows green. Only entry actions write the lamp, and `standby` has none. In standby the lamp should show yellow:

<LiveEmbed example={step4} show={['TrafficLight']} />

<details>
<summary>The verdicts Live shows</summary>

- <CaseVerdict example={step4} name="LightCanTurnGreen" />
- <CaseVerdict example={step4} name="NeverBothGreen" />
- <CaseVerdict example={step4} name="NeverGreenInStandby" />
- <CaseVerdict example={step4} name="GreenLampOnlyInGreen" />

</details>

The model does not track the flashing.

## Events and the Environment

So far the light enters and leaves standby at any time. An operator decides: the light receives a `pause` or a `resume` event, and the standby transitions fire only when their event is present.

<LiveEmbed example={step5} show={['Event', 'Trigger', 'EventTrigger']} />

A statechart holds at most one current event, as it holds one active state, and `isPresent` has the shape of `isActive`. A `Trigger` decides whether a transition may fire; the base class always allows it, so the color transitions stay as they were, and an `EventTrigger` allows it only while its event is present.

<LiveEmbed example={step5} show={['Transition', 'Statechart']} />

<LiveEmbed example={step5} show={['TrafficLight']} />

The standby transitions replace their inherited trigger with an `EventTrigger` by [`redefine contains`](../../language/features.md#redefine-and-redefines), and bind its event in a nested block of their own.

Events come from outside the light, so the cases send them: before each step, [`havoc`](../../language/operations.md#havoc) on the light's `event` gives it any of its events, or none, and the verifier explores each. A new case's environment never sends `pause`; the `assume` after the `havoc` drops the steps in which it would:

<LiveEmbed example={step5} show={['NoStandbyWithoutPause']} />

<details>
<summary>The verdicts Live shows</summary>

- <CaseVerdict example={step5} name="LightCanTurnGreen" />
- <CaseVerdict example={step5} name="NeverBothGreen" />
- <CaseVerdict example={step5} name="NeverGreenInStandby" />
- <CaseVerdict example={step5} name="GreenLampOnlyInGreen" />
- <CaseVerdict example={step5} name="NoStandbyWithoutPause" />

</details>

## An Emergency Override

When an emergency vehicle approaches, both lights of the intersection turn red and stay red until the emergency is over, whatever state they are in. One emergency signal serves every intersection of a town, so it belongs to no light and no intersection: it is a [global feature](../../language/features.md#top-level-global-features), declared outside every class and reached by name.

<LiveEmbed example={advanced} show={['Emergency', 'emergency']} />

The intersection consults it before it lets a light step. During an emergency it activates `operating` in both lights, which enters `red`, whose entry action switches the lamp to red; the [`if`](../../language/operations.md#if) is decided in every step, as in [The Minimum Green Time](green-time.md).

<LiveEmbed example={advanced} show={['Intersection']} />

A case lets the emergency start and end at any step:

<LiveEmbed example={advanced} show={['EmergencyStopsTraffic']} />

As in Java, `&&` binds tighter than `||` ([operator precedence](../../language/expressions.md#operator-precedence)); the parentheses only make the grouping visible.

<details>
<summary>The verdicts Live shows</summary>

- <CaseVerdict example={advanced} name="LightCanTurnGreen" />
- <CaseVerdict example={advanced} name="NeverBothGreen" />
- <CaseVerdict example={advanced} name="NeverGreenInStandby" />
- <CaseVerdict example={advanced} name="GreenLampOnlyInGreen" />
- <CaseVerdict example={advanced} name="NoStandbyWithoutPause" />
- <CaseVerdict example={advanced} name="EmergencyStopsTraffic" />

</details>

The whole model:

<LiveEmbed example={advanced} />

## Where the Lessons End

You can now read and write a statechart library with nested states, entry actions, events and an environment. The production libraries build on the same patterns: Gamma's adds orthogonal regions, exit actions, guards and transitions across levels of the hierarchy, and SysML v2's adds parallel states and time triggers. Every construct is in the [language reference](../../language/index.md), and [how the language works](../../how-it-works/index.mdx) shows how a model becomes what a backend checks.
