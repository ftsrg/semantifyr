---
title: 'Advanced Tutorial'
sidebar_position: 3
---

import LiveEmbed from '@site/src/components/LiveEmbed';
import step1 from '@oxsts-test-models/tutorial/advanced-step1.oxsts';
import step2 from '@oxsts-test-models/tutorial/advanced-step2.oxsts';
import step3 from '@oxsts-test-models/tutorial/advanced-step3.oxsts';
import step4 from '@oxsts-test-models/tutorial/advanced-step4.oxsts';
import step5 from '@oxsts-test-models/tutorial/advanced-step5.oxsts';
import advanced from '@oxsts-test-models/tutorial/advanced.oxsts';

# Advanced Tutorial

This is the third tutorial in the Traffic Light series. It extends the small `Statechart` library from [Building a Library](building-a-library.md) with the patterns that real semantic libraries like Gamma's `Statecharts.oxsts` and SysML v2's `States.oxsts` are built from: hierarchical states, entry actions, and events with triggers. A last step adds a global emergency override that pre-empts the traffic lights without changing their definition. The steps add one pattern at a time, and the verification cases of earlier steps keep running against the extended model.

## Requirements

A browser is enough. As in the previous tutorials, every step ends in a complete model that the **Open this step in the editor** button loads into a live editor embedded in this page; the [Direct Modeling](direct-modeling.md#the-live-editor) tutorial describes the editor. See [Getting Started](../../getting-started.md) for the other ways to run Semantifyr.

:::tip Finished example
The completed model is at the end of this page under [Snapshot](#snapshot). The standalone live editor also offers it in its model picker as *Tutorial: Advanced*.
:::

The tutorial starts from the final model of Building a Library. Each step is a separate file with its own package below `example::trafficlight::advanced`:

<LiveEmbed code={step1} show={['package']} />

## Composite States

A real traffic light does more than cycle its colors. At night or during maintenance it switches to a standby mode in which it only flashes yellow, and when it comes back it starts again from red. The natural model is a hierarchy: the three colors become substates of an `operating` state, and a `standby` state sits next to `operating`.

A composite state owns states of its own, keeps track of the active one, and is itself a state of its parent. `Statechart` already does the first two, so make `State` extend `Statechart`:

<LiveEmbed code={step1} show={['State']} />

Every state is now also a statechart: it inherits `states`, `transitions` and `activeState`, and a state without substates leaves them empty. The `parent` feature keeps its type `Statechart`, which now fits both levels: the parent of `operating` is the traffic light, and the parent of `red` is `operating`.

The `initial` reference names the substate to enter when the state itself is entered. Its multiplicity `[0..1]` lets a state without substates leave it unbound, so it holds `nothing`. The `activate` transition activates the state and then its initial substate. Like `inline for`, an [`inline if`](../../language/inline-operations.md#inline-if) is decided when the model is compiled: for a state without an initial substate the whole block disappears, so the chain of `activate` calls ends at the innermost state.

Now restructure the traffic light:

<LiveEmbed code={step1} show={['TrafficLight']} />

The nested block of `operating` declares the three color states. Inside the block, `states` is the `states` feature of `operating`, so the colors become substates of `operating` rather than of the light. The block also binds `initial` to `red`.

The transitions stay in the traffic light, where the inherited main transition fires them. A transition only needs to name its two states, and `operating.red` navigates into `operating` to find one. The two new transitions connect `operating` and `standby` in both directions; for now they can fire at any time. The `init` transition activates `operating`, which in turn activates `red`.

The intersection and the two verification cases from Building a Library now ask for `operating.green` instead of `green`. Add a case that checks that the light is never green in standby:

<LiveEmbed code={step1} show={['NeverGreenInStandby']} />

**Task:** Open the step in the editor and verify all cases.

**Check:** `GreenColorIsReachable` and `IntersectionNeverBothGreen` pass, but `NeverGreenInStandby` fails. Open its witness and switch to *State*: in the last state, the light's `activeState` is `standby` while `operating.activeState` still holds `green`.

## Keeping Parents and Children Consistent

The counterexample shows the bookkeeping a hierarchy needs. A state asks only its parent whether it is active, and `operating` remembers its last color after the light has left it, so that color still counts as active. Worse, the color transitions keep firing in standby, because `redToGreen` only asks whether `red` is active. A substate can be active only while its parent is active. Make the check recursive:

<LiveEmbed code={step2} show={['State', 'Statechart']} />

The traffic light is not a state of anything; while it runs, it is active, so `Statechart` declares `isActive` returning `true`. `State` redefines it: a state is active if its parent holds it as the active state and the parent itself is active. The call `parent.isActive()` is resolved for each state when the model is compiled, so the calls walk up the hierarchy and end at the traffic light.

`operating` still remembers its last color while the light is in standby, but that no longer matters: the color is not active while `operating` is not, and entering `operating` again starts from its initial state.

**Task:** Open the step and verify all cases again.

**Check:** All three cases pass.

## Entry Actions

An entry action runs whenever a state becomes active, whichever transition enters it. Add an empty `entry` transition to `State` and call it from `activate`:

<LiveEmbed code={step3} show={['State']} />

`activate` runs the entry action after the state has become active and before its initial substate is entered, so a parent's entry action runs before its children's.

Each state needs its own entry action, but a nested block holds features only: it can bind `initial` or `from`, but it cannot redefine a transition. Behavior that differs between states therefore goes into a subclass of `State`, and the data that differs between instances of that subclass goes into their nested blocks. Give the traffic light a lamp, and declare a `LampState` that switches the lamp to its color on entry:

<LiveEmbed code={step3} show={['LightColor', 'Lamp', 'LampState']} />

`LightColor` returns from the Direct Modeling tutorial, with an `Off` literal for a lamp that has not been switched on yet. `LampState` redefines `entry` to write its `color` to its `output` lamp; each instance binds both references.

<LiveEmbed code={step3} show={['TrafficLight']} />

The traffic light contains the lamp, and each color is now a `LampState` that binds `output` to the light's lamp and `color` to its color. In standby the lamp shows yellow; the model does not track the flashing.

Add a case that checks that the lamp shows green only while the green state is active:

<LiveEmbed code={step3} show={['GreenLampOnlyInGreen']} />

Only entry actions write the lamp, so the case checks that every state that can follow `green` switches the lamp, including `standby`.

**Task:** Open the step and verify all cases. Then make `standby` a plain state (`contains standby: State subsets states`) and verify `GreenLampOnlyInGreen` again.

**Check:** All cases pass. Without its entry action, `standby` leaves the lamp as it was: `GreenLampOnlyInGreen` fails, and its witness ends with the light in standby and the lamp showing green.

## Events and Triggers

So far the light enters and leaves standby at any time. In a real controller an operator decides: the light receives a `pause` or a `resume` event, and the standby transitions fire only when their event is present. Declare the events and the triggers:

<LiveEmbed code={step4} show={['Event', 'Trigger', 'EventTrigger']} />

An event belongs to a statechart, which holds at most one current event in the same way it holds one active state: `isPresent` reads "I am present if my parent's current event is me", the same shape as `isActive`. A `Trigger` decides whether a transition may fire. The base class always allows it, which keeps the color transitions as they are, and an `EventTrigger` allows it only while its event is present.

<LiveEmbed code={step4} show={['Transition', 'Statechart']} />

Each transition contains a trigger, and `isEnabled` requires it. The statechart contains its events and a feature-typed variable `event` for the current one, which starts as `nothing`.

<LiveEmbed code={step4} show={['TrafficLight']} />

The traffic light declares its two events. The nested blocks of the standby transitions use `redefine contains` to replace the inherited `Trigger` with an `EventTrigger`, and bind its `event` in a nested block of their own.

Events come from outside the light, so the verification cases send them: before each step, [`havoc`](../../language/operations.md#havoc) on the light's `event` lets the light receive any of its events, or none, and the model checker explores every choice. Add a case whose environment never sends `pause`:

<LiveEmbed code={step4} show={['NoStandbyWithoutPause']} />

The `assume` after the `havoc` removes the steps in which the event would be `pause`. The cases from the earlier steps now havoc the event of every light they contain before it steps, so their lights still reach standby.

**Task:** Open the step and verify all cases.

**Check:** All cases pass. Without `pause` the light never enters standby, while the color transitions, which have no event trigger, fire as before.

## A Global Emergency Override

The last pattern changes no class of the library and leaves the traffic light as it is. When an emergency vehicle approaches, every light of the intersection must turn red and stay red until the emergency is over, whatever state it is in. The emergency signal is not part of any light or intersection: one signal serves every intersection of a town. Declare it as a [global feature](../../language/features.md#top-level-global-features):

<LiveEmbed code={step5} show={['Emergency', 'emergency']} />

A `global containment` declares an instance outside every class, in the universe around the system under verification. Any class of the package can refer to it by name, without a reference wired through the containment tree.

The intersection consults the emergency before it lets a light take a step:

<LiveEmbed code={step5} show={['Intersection']} />

During an emergency, the intersection activates the `operating` state of both lights instead of firing their transitions. Activating `operating` enters its initial state `red`, whose entry action switches the lamp to red, so the hierarchy and the lamp stay consistent even if the light was in standby. Unlike `inline if`, the [`if`](../../language/operations.md#if) operation is decided at runtime, in every step, by the current value of `emergency.active`.

Add a case that lets the emergency start and end at any step:

<LiveEmbed code={step5} show={['EmergencyStopsTraffic']} />

Mind the parentheses around the conjunction: `&&` and `||` share one [precedence level](../../language/expressions.md#operator-precedence) and associate to the left, so without them the property would read `(!emergency.active || ...) && ...` and fail.

**Task:** Open the step and verify all cases.

**Check:** All cases pass: whenever the emergency is active, both lamps show red.

## Wrap-up

This tutorial extended the small statechart library and its traffic light with four patterns:

- Hierarchical states: a `State` that is also a `Statechart`, an `initial` substate entered by `activate`, and an `isActive` that asks the whole chain of parents
- Entry actions: an empty `entry` transition in the library, redefined by a `State` subclass, with the per-state data bound in nested blocks
- Events and triggers: events held by the statechart like its states, one current event per step, and a default `Trigger` that `redefine contains` replaces with an `EventTrigger`
- A global emergency override: a `global containment` instance that the intersection consults before it lets the lights step

Along the way you used `inline if` for decisions made at compile time, `redefine contains` with a subclass, `havoc` on a feature-typed variable to model the environment, and the runtime `if`.

The production libraries build on the same patterns: Gamma's `Statecharts.oxsts` adds orthogonal regions, exit actions, guards and transitions across levels of the hierarchy, and the SysML v2 library adds parallel states and time triggers. For deeper details on any construct, see the [language reference](../../language/index.md).

## Snapshot

The complete final model:

<LiveEmbed code={advanced} />
