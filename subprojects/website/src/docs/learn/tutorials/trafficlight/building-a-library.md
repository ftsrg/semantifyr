---
title: 'Building a Library'
sidebar_position: 2
---

import LiveEmbed from '@site/src/components/LiveEmbed';
import step1 from '@oxsts-test-models/tutorial/intermediate-step1.oxsts';
import step2 from '@oxsts-test-models/tutorial/intermediate-step2.oxsts';
import step3 from '@oxsts-test-models/tutorial/intermediate-step3.oxsts';
import intermediate from '@oxsts-test-models/tutorial/intermediate.oxsts';

# Building a Library

This is the second tutorial in the Traffic Light series. It refactors the basic traffic light from the [Direct Modeling](direct-modeling.md) tutorial into a small reusable library: a `Statechart` base class with first-class `State` and `Transition` instances. The traffic light becomes a thin specialization of `Statechart`. This is the same pattern used by real semantic libraries like Gamma's `Statecharts.oxsts` and SysML v2's `States.oxsts`.

## Requirements

A browser is enough. As in [Direct Modeling](direct-modeling.md), every step ends in a complete model that the **Open this step in the editor** button loads into a live editor embedded in this page; the [Direct Modeling](direct-modeling.md#the-live-editor) tutorial describes the editor. See [Getting Started](../../getting-started.md) for the other ways to run Semantifyr.

:::tip Finished example
The completed model is at the end of this page under [Snapshot](#snapshot). The standalone live editor also offers it in its model picker as *Tutorial: Intermediate*.
:::

In the Direct Modeling tutorial the entire state machine lived inside a single class: a `LightColor` enum, a `var color`, and a main transition that hard-coded the cycle as a `choice` of guarded branches. That works for one specific traffic light, but it does not scale: every new state or transition forces another branch in the `choice`, and the host has no way to query the structure of its own state machine. By the end of this tutorial the `LightColor` enum and the `assumeColor`, `setColor` and `isColor` helpers will be gone, replaced by a small reusable `Statechart` base class that any state-based system could extend.

The design changes significantly, so this tutorial starts from a fresh file with its own package:

<LiveEmbed code={step1} show={['package']} />

## States and Transitions

The most basic building block is a `State`. A state knows whether it is currently active by consulting its containing statechart, and exposes an `activate` transition that other code can call to switch into it. Declare the `State` class:

<LiveEmbed code={step1} show={['State']} />

The `container` keyword declares the inverse of a `contains` feature. The `Statechart` class (which we add in the next section) will own its `State` instances through `contains states`, and each `State` knows its containing statechart through `container parent`. The `opposite` keyword wires the two directions together: the validator checks that opposite features name each other, so declaring one direction without the other is an error.

The `isActive` property reads "I am active if my parent's `activeState` is me". The `self` keyword always refers to the current instance. The `activate` transition is the only place where a state changes its host: it assigns `self` to the parent's `activeState`. Keeping this assignment in `activate` puts the rule for how a state becomes active in one place, which transitions rely on below.

:::note
`Statechart` is declared in the next section. Forward references between classes are allowed.
:::

Next, declare a `Transition` class below `State`:

<LiveEmbed code={step1} show={['Transition']} />

A transition is a first-class object with two references and the behavior "if I am enabled, activate my destination state". Modeling transitions as their own class lets the host hold a _collection_ of them and pick one to fire.

The `refers` keyword declares a **reference feature**: unlike `contains` (which establishes ownership), `refers` is a non-owning pointer. Each transition points to two `State` instances but does not own them; the statechart owns the states. The default multiplicity is `[1]` (exactly one), which is what we want here, so we omit it.

The main transition first assumes `isEnabled()`, then `inline`s the destination state's `activate` transition. Inlining substitutes the body of `to.activate()` at the call site, which assigns the destination to the parent's `activeState`. The source state needs no explicit clearing: both states share the same parent, so the assignment overwrites the previous value.

## The Statechart Base Class

Now declare the `Statechart` class that hosts states and transitions:

<LiveEmbed code={step1} show={['Statechart']} />

The `Statechart` owns _any number_ of states and transitions through containment features with multiplicity `[0..*]` (zero or more). A generic statechart does not fix the count; concrete subclasses decide which instances actually appear.

The `var activeState` is a **feature-typed variable**: its type is the `states` feature, not a class, and its values are the instances reachable through `states`. The compiler turns it into an integer when it flattens the model. The `[0..1]` multiplicity means it holds one of the contained states or `nothing`, the literal for the absence of a value. Starting from `nothing` means a fresh statechart is _inactive_ until something assigns a state to `activeState`.

The `@Control` annotation marks the variable as important to track during verification.

The main transition uses `inline for choice` over the `transitions` collection. This compile-time **inline operation** unrolls into a non-deterministic `choice` with one branch per transition; the model checker only sees the resulting `choice`. Each branch inlines the transition's main transition, which first assumes `isEnabled()`, so a disabled transition blocks its own branch: only enabled transitions can fire on a given step.

When `activeState` is `nothing`, _no_ state is active, so every transition is disabled and the whole `choice` blocks. An inactive statechart does nothing until something assigns a state to `activeState`.

**Task:** Open the step in the editor. The library validates without errors; it has no verification case yet.

## Refactoring the Traffic Light

Now declare a `TrafficLight` class that extends `Statechart`:

<LiveEmbed code={step2} show={['TrafficLight']} />

The `class TrafficLight : Statechart` syntax declares **inheritance**: `TrafficLight` extends `Statechart` and inherits all of its members (`states`, `transitions`, `activeState`, and the main transition). A class has at most one superclass.

Each color is its own contained `State` instance. The `subsets` modifier on the `red`, `yellow`, and `green` features declares that they are also part of the inherited `states` collection. The three transitions are subsets of the inherited `transitions` collection in the same way, so the `inline for choice (transition in transitions)` inherited from `Statechart` iterates over all three of them.

Each `Transition` is contained with a **nested redefinition block** (`{ ... }` after the feature declaration). Inside the block we redefine the `from` and `to` references of the contained `Transition` and bind them to the host's states with `=`. The `=` form binds a feature to a _compile-time_ expression, fixed when the model is compiled. Compare it with `:=`, the runtime assignment of variables.

Finally, `redefine init { activeState := red }` overrides the inherited `init` transition so the light starts in a known state. Like the main transition, `init` is inherited from the implicit `Anything` base class with an empty body, so it has to be redefined.

Add a verification case that asks whether the light can turn green:

<LiveEmbed code={step2} show={['GreenColorIsReachable']} />

**Task:** Open the step and verify `GreenColorIsReachable`.

**Check:** It fails. Only the `init` and main transitions of the verified class run: `GreenColorIsReachable` inlines the light's main transition, but nothing calls the light's `init`, so `activeState` stays `nothing`, no transition is ever enabled, and the light never turns green. A failed reachability property has no witness to show.

## The Init Chain

Delegate the initialization to the light:

<LiveEmbed code={step3} show={['GreenColorIsReachable']} />

The `redefine init` of the verification case inlines the light's `init`. This is the **init chain pattern**: the `init` of a class calls the `init` of each contained instance that needs one, so the whole hierarchy is initialized in one go.

**Task:** Open the step and verify `GreenColorIsReachable` again.

**Check:** It passes. In the *State* view of the witness, `root.light.activeState` starts as `nothing` and holds `root.light.green` after the first step.

## The Intersection

Now declare the `Intersection` class. It composes two traffic lights and chains their initialization:

<LiveEmbed code={intermediate} show={['Intersection']} />

The `redefine init` block initializes both lights, continuing the init chain. The main transition is the coordinated step from the Direct Modeling tutorial, but the guard now uses `green.isActive()` instead of the old `isColor(LightColor::Green)`: it navigates to the `green` state of the other light and asks whether it is currently active.

Finally, add the safety case:

<LiveEmbed code={intermediate} show={['IntersectionNeverBothGreen']} />

Both verification cases follow the same pattern: a single `contains` feature for the system under verification, a `redefine init` that starts the init chain, a main transition that lets the system take a step, and a `prop` that queries the state instances directly through `isActive()`.

**Task:** Open the step and verify both cases.

**Check:** Both pass, as in the Direct Modeling tutorial: the green state is reachable, and the guard prevents both lights from being green at the same time.

## Wrap-up

This tutorial refactored a one-class traffic light into a small reusable `Statechart` base class with first-class `State` and `Transition` instances. Along the way you used:

- Inheritance with the `:` syntax
- `container` and `opposite` for bidirectional features
- `contains ... subsets ...` for putting one feature into a parent collection
- Multiplicities `[1]`, `[0..1]`, `[0..*]`
- `refers` for non-owning references
- Feature-typed variables and the `nothing` literal
- Nested redefinition blocks and the compile-time binding form `=`
- `inline for choice` for compile-time unrolling over a feature collection
- `inline` calls that delegate behavior between transitions
- `redefine init` and the init chain pattern for one-time setup
- The `@Control` annotation

For deeper details on any of these constructs, see the [language reference](../../language/index.md).

The [Advanced Tutorial](advanced-tutorial.md) is planned to extend this small library with the patterns real semantic libraries use.

## Snapshot

The complete final model:

<LiveEmbed code={intermediate} />
