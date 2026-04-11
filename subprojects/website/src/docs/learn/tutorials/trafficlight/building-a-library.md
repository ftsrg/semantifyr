---
title: 'Building a Library'
sidebar_position: 2
---

# Building a Library

This is the second tutorial in the Traffic Light series. It refactors the basic traffic light from the [Direct Modeling](direct-modeling.md) tutorial into a small reusable library: a `Statechart` base class with first-class `State` and `Transition` instances. The traffic light becomes a thin specialization of `Statechart`. This is the same pattern used by real semantic libraries like Gamma's `Statecharts.oxsts` and SysML v2's `States.oxsts`.

## Requirements

You can follow this tutorial in the **live editor** directly in your browser: [Open the live editor](https://live.semantifyr.org). No setup required. See [Getting Started](../../getting-started.md) for other options like the JupyterHub environment.

:::tip Finished example
If you want to skip ahead and see the completed model, use the **Load example** button in the toolbar and select *Tutorial: traffic light (statechart library)*.
:::

The design changes significantly compared to the Direct Modeling tutorial, so start with a fresh file rather than editing the previous one. Create a new file alongside `trafficlight.oxsts` with a new package declaration:

```oxsts
package example::trafficlight::intermediate
```

In the Direct Modeling tutorial the entire state machine lived inside a single class: a `LightColor` enum, a `var color`, and a `redefine tran` body that hard-coded the cycle as a `choice` of guarded branches. That works for one specific traffic light, but it does not scale: every new state or transition forces another branch in the `choice`, and the host has no way to query the structure of its own state machine. By the end of this tutorial the `LightColor` enum and the `assumeColor`/`setColor`/`isColor` helpers will be gone, replaced by a small reusable `Statechart` base class that any state-based system could extend.

## States and Transitions

The most basic building block is a `State`. A state knows whether it is currently active by consulting its containing statechart, and exposes an `activate` transition that other code can call to switch into it. Declare the `State` class:

```oxsts
class State {
    container parent: Statechart opposite states

    prop isActive(): bool {
        return parent.activeState == self
    }

    tran activate() {
        parent.activeState := self
    }
}
```

The `container` keyword declares the inverse of a `contains` feature. The `Statechart` class (which we add in the next section) will own its `State` instances via `contains states: State[...]`, and each `State` will know its containing statechart through `container parent`. The `opposite` keyword wires the two directions together: a validation rule checks that opposite features are reciprocal, so declaring one direction without the other is an error.

The `isActive` property reads "I am active if my parent's `activeState` is me". The `self` keyword always refers to the current instance. The `activate` transition is the only place where a state mutates its host: it assigns `self` to the parent's `activeState`. Encapsulating this assignment in `activate` keeps the rule about how a state becomes active in one place, which we will rely on later when transitions fire.

:::note
`Statechart` does not exist yet; we declare it in the next section. Forward references between classes are allowed.
:::

Next, declare a `Transition` class below `State`:

```oxsts
class Transition {
    refers from: State
    refers to: State

    prop isEnabled(): bool {
        return from.isActive()
    }

    redefine tran {
        assume (isEnabled())
        inline to.activate()
    }
}
```

A transition is a first-class object with two references and the behavior of "if I am enabled, activate my destination state". Modeling transitions as their own class lets the host hold a _collection_ of them and pick one to fire.

The `refers` keyword declares a **reference feature**: unlike `contains` (which establishes ownership), `refers` is a non-owning pointer. Each transition points to two `State` instances but does not own them; the statechart owns the states. The default multiplicity is `[1]` (exactly one), which is what we want here, so we can omit the bracket.

The redefined main transition first asserts `isEnabled()`, then `inline`s the destination state's `activate` transition. Inlining substitutes the body of `to.activate()` at the call site, which in turn assigns the parent's `activeState` to the destination. We do not need to clear the source state explicitly because both states share the same parent, so the assignment overwrites the previous value.

## The Statechart Base Class

Now declare the `Statechart` class that will host states and transitions:

```oxsts
class Statechart {
    contains states: State[0..*] opposite parent
    contains transitions: Transition[0..*]

    @Control
    var activeState: states[0..1] := nothing

    redefine tran {
        inline for choice (transition in transitions) {
            inline transition.main()
        }
    }
}
```

The `Statechart` owns _any number_ of states and transitions through containment features with multiplicity `[0..*]` (zero or more). This is more flexible than fixing the count: a generic statechart can have any number of children, and concrete subclasses pin down which instances actually appear.

The `var activeState` is a **feature-typed variable**: its type is the `states` feature, not a class, and its value domain is the set of instances reachable through `states`. The compiler turns it into an integer at flatten time. The `[0..1]` multiplicity means it can hold one of the contained states or `nothing`. The `nothing` literal denotes the absence of a value, and is the alternative inhabitant of an optional `[0..1]` slot. Initializing `activeState` to `nothing` means a fresh statechart is _inactive_ until something explicitly assigns a state to it.

The `@Control` annotation marks the variable as important to track, so the verifier may report it specifically in counterexamples.

The redefined main transition uses `inline for choice` over the `transitions` collection. This is a compile-time **inline operation** that unrolls into a non-deterministic `choice` of one branch per element. With three transitions, the body becomes a `choice` of three branches after unrolling. The unrolling happens at compile time; at runtime the model checker only sees the resulting `choice`. Each `inline transition.main()` invokes the chosen transition's implicit `main`, which first asserts `assume(isEnabled())`. Disabled transitions therefore block their own branch automatically: only enabled transitions can fire on a given step.

When `activeState` is `nothing`, _no_ state is active, so every transition's `from.isActive()` returns false, every `assume(isEnabled())` blocks, and the whole `choice` becomes unfireable. An inactive statechart simply does nothing until something assigns a state to `activeState`.

## Refactoring the Traffic Light

Now declare a `TrafficLight` class that extends `Statechart`:

```oxsts
class TrafficLight : Statechart {
    contains red: State subsets states
    contains yellow: State subsets states
    contains green: State subsets states

    contains redToGreen: Transition subsets transitions {
        redefine refers from: State[1] = red
        redefine refers to: State[1] = green
    }
    contains greenToYellow: Transition subsets transitions {
        redefine refers from: State[1] = green
        redefine refers to: State[1] = yellow
    }
    contains yellowToRed: Transition subsets transitions {
        redefine refers from: State[1] = yellow
        redefine refers to: State[1] = red
    }

    redefine init {
        activeState := red
    }
}
```

The `class TrafficLight : Statechart` syntax declares **single inheritance**: `TrafficLight` extends `Statechart` and inherits all of its members (`states`, `transitions`, `activeState`, and the redefined main transition).

Each color is its own contained `State` instance. The _subsets_ modifier on the `red`, `yellow`, and `green` features declares that they are also part of the inherited `states` collection: a single feature can be put into a more general parent feature without redefining it. The same `subsets` mechanism applies to the three transitions, which are all declared as subsets of the inherited `transitions` collection. Because of `subsets`, the `inline for choice (transition in transitions)` body inherited from `Statechart` will iterate over all three of them automatically.

Each `Transition` is contained with a **nested redefining block** (`{ ... }` after the feature name). Inside the block we redefine the `from` and `to` references on the contained `Transition` and bind them to the host's state instances using `=`. The `=` form binds a feature to a _compile-time_ expression: the binding is fixed when the model is unfolded. Compare with `:=`, which is the runtime assignment operator for variables.

Finally, `redefine init { activeState := red }` overrides the inherited `init` transition so the light begins in a known state. The `init` kind is inherited from the implicit `Anything` base class with an empty default body, so we have to `redefine` it the same way we redefine `tran`. The `init` transition runs once at startup.

Now declare the `Intersection` class. It composes two traffic lights and chains their initialization through `init`:

```oxsts
class Intersection {
    contains north: TrafficLight
    contains east: TrafficLight

    redefine init {
        inline north.init()
        inline east.init()
    }

    redefine tran {
        choice {
            assume (!east.green.isActive())
            inline north.main()
        } or {
            assume (!north.green.isActive())
            inline east.main()
        }
    }
}
```

The `redefine init` block delegates initialization to the contained lights by `inline`-calling each one's `init` transition. This is the **init chain pattern**: a host class's `init` walks its containment tree and calls `init` on each child, so the whole hierarchy becomes active in one go. There is no separate `start` transition: initialization happens entirely through `init` cascades.

The `redefine tran` body is the same coordinated step from the Direct Modeling tutorial, but the safety guard now uses `green.isActive()` instead of the old `isColor(LightColor::Green)`: it navigates to the `green` state on the other light and asks whether it is currently active.

Finally, declare the verification cases:

```oxsts
@VerificationCase
class GreenColorIsReachable {
    contains light: TrafficLight[1]

    redefine init {
        inline light.init()
    }

    redefine tran {
        inline light.main()
    }

    prop {
        return EF light.green.isActive()
    }
}

@VerificationCase
class IntersectionNeverBothGreen {
    contains intersection: Intersection

    redefine init {
        inline intersection.init()
    }

    redefine tran {
        inline intersection.main()
    }

    prop {
        return AG !(intersection.north.green.isActive() && intersection.east.green.isActive())
    }
}
```

Both verification cases follow the same pattern: a single `contains` feature for the system under test, a `redefine init` that delegates initialization down the chain via a single `inline ... .init()` call, a `redefine tran` that delegates each step via `inline ... .main()`, and a `prop` that uses `green.isActive()` to query the state instances directly.

**Task** Run the verifications on `GreenColorIsReachable` and `IntersectionNeverBothGreen`.

**Check** Both produce the same answers as in the Direct Modeling tutorial: `GreenColorIsReachable` succeeds because the green state is reachable, and `IntersectionNeverBothGreen` succeeds because the safety guard prevents both lights from being green at the same time.

## Wrap-up

This tutorial refactored a one-class traffic light into a small reusable `Statechart` base class with first-class `State` and `Transition` instances. Along the way you used:

- Single inheritance with the `:` syntax
- `container` and `opposite` for bidirectional features
- `contains ... subsets ...` for putting one feature into a parent collection
- Multiplicities `[1]`, `[0..1]`, `[0..*]`
- `refers` for non-owning references
- Feature-typed variables and the `nothing` literal
- Nested feature redefining blocks and the compile-time binding form `=`
- `inline for choice` for compile-time unrolling over a feature collection
- `inline` calls that delegate behavior between transitions
- `redefine init` and the init chain pattern for one-time setup
- The `@Control` annotation

For deeper details on any of these constructs, see the [language reference](../../language/index.md).

The next tutorial in the series, the [Advanced Tutorial](advanced-tutorial.md), extends this small library with more advanced patterns: hierarchical states, transitions with optional triggers, entry actions, and other idioms used by real semantic libraries. *(That tutorial is currently a placeholder while the content is being written.)*

## Snapshot

The complete final state of `trafficlight.oxsts`:

```oxsts verify open=trafficlight-library-snapshot
package example::trafficlight::intermediate

class State {
    container parent: Statechart opposite states

    prop isActive(): bool {
        return parent.activeState == self
    }

    tran activate() {
        parent.activeState := self
    }
}

class Transition {
    refers from: State
    refers to: State

    prop isEnabled(): bool {
        return from.isActive()
    }

    redefine tran {
        assume (isEnabled())
        inline to.activate()
    }
}

class Statechart {    
    contains states: State[0..*] opposite parent
    contains transitions: Transition[0..*]

    @Control
    var activeState: states[0..1] := nothing

    redefine tran {
        inline for choice (transition in transitions) {
            inline transition.main()
        }
    }
}

class TrafficLight : Statechart {
    contains red: State subsets states
    contains yellow: State subsets states
    contains green: State subsets states

    contains redToGreen: Transition subsets transitions {
        redefine refers from: State[1] = red
        redefine refers to: State[1] = green
    }
    contains greenToYellow: Transition subsets transitions {
        redefine refers from: State[1] = green
        redefine refers to: State[1] = yellow
    }
    contains yellowToRed: Transition subsets transitions {
        redefine refers from: State[1] = yellow
        redefine refers to: State[1] = red
    }

    redefine init {
        activeState := red
    }
}

class Intersection {
    contains north: TrafficLight
    contains east: TrafficLight

    redefine init {
        inline north.init()
        inline east.init()
    }

    redefine tran {
        choice {
            assume (!east.green.isActive())
            inline north.main()
        } or {
            assume (!north.green.isActive())
            inline east.main()
        }
    }
}

@VerificationCase
class GreenColorIsReachable {
    contains light: TrafficLight[1]

    redefine init {
        inline light.init()
    }

    redefine tran {
        inline light.main()
    }

    prop {
        return EF light.green.isActive()
    }
}

@VerificationCase
class IntersectionNeverBothGreen {
    contains intersection: Intersection

    redefine init {
        inline intersection.init()
    }

    redefine tran {
        inline intersection.main()
    }

    prop {
        return AG !(intersection.north.green.isActive() && intersection.east.green.isActive())
    }
}
```
