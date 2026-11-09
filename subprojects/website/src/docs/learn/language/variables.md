---
title: Variables
sidebar_position: 7
---

# Variables

Variables hold the **mutable state** of the system. Unlike [features](features.md), which define structure and are eliminated during flattening, variables persist across [transitions](transitions.md) and are tracked by the model checker.

## Variable Declaration 

A variable declaration consists of the `var` keyword, a name, a type and an initializer. Both are required: the validator rejects a variable without a type (`OXSTS.VARIABLE_WITH_IMPLICIT_TYPE`) and one without an initializer (`OXSTS.MISSING_INITIALIZER`). The loop variable of a `for` and the parameters of a transition or property take their values from the range and the arguments and have no initializer.

```oxsts fragment
var value: int := 0
```

## Initial States

The initializers give the declaration valuation, in which every variable holds the value of its initializer. The declaration valuation is not a state of the system: the initial states are the states the [`init`](transitions.md) transition reaches from it, and the property is checked on the initial states and on every state reachable from them. A variable that `init` assigns is observed only with the value `init` gives it.

An arbitrary initial value is a [`havoc`](operations.md#havoc) in `init`. Here `value` starts from any integer, and the declared 0 is never observed:

```oxsts
class Counter {
  var value: int := 0

  redefine init {
    havoc(value)
  }
}
```

When no execution of `init` is enabled, as with `init { assume(false) }`, the system has no states: every `AG` property holds and no `EF` target is reached. Nothing reports such an `init`.

## Multiplicity on the Type

A variable type may include a multiplicity bracket. This is how optional values are declared. A bounded multiplicity above one declares a fixed-size array, such as `var slots: int[3] := [0, 0, 0]`, whose initializer gives every slot. An array of class or feature type, such as `var queue: Job[2] := [first, second]`, holds one instance per slot, and a call through a slot, such as `inline queue[i].finish()`, runs on the instance the slot holds. An array is read and written slot by slot (`slots[i]`), or as a whole: `slots := other`, `slots := [0, 0, 0]`, `havoc(slots)` and `slots == other` act on every slot.

```oxsts fragment
var occupant: Item[0..1] := nothing
```

## Feature-Typed Variables

A variable's type may be a [feature](features.md). The value domain of such a *feature-typed variable* is the set of instances reachable through that feature. A call through the variable, such as `inline active.step()`, runs the call on the instance the variable holds; the validator rejects member access through a variable or an array slot, such as `active.value` (`OXSTS.UNSUPPORTED_FEATURE`).

```oxsts fragment
class Region {
  contains substates: Substate[0..*]

  var active: substates[0..1] := nothing
}
```
