---
title: Variables
sidebar_position: 7
---

# Variables

Variables hold the **mutable state** of the system. Unlike [features](features.md), which define structure and are eliminated during flattening, variables persist across [transitions](transitions.md) and are tracked by the model checker.

## Variable Declaration 

A variable declaration consists of the `var` keyword, a name, and a type. Either the type or an initializer can be omitted.

Explicit type and initializer:

```oxsts
var value: int := 0
```

Initializer only. The type is inferred from the initializer.

```oxsts
var value := 0
```

Type only. The variable is uninitialized at the point of declaration.

```oxsts
var value: int
```

## Uninitialized Variables Are Havoced

A variable declared **without** an initializer is unbound at declaration time. Before the [`init`](transitions.md) transition runs, the verifier `havoc`s every uninitialized variable, picking an arbitrary value of its type. This causes the model checker to explore all possible starting values.

To start from a known value, supply an initializer or assign the variable inside the [`init`](transitions.md) transition:

```oxsts
class Counter {
  var value: int

  redefine init {
    value := 0
  }
}
```

## Multiplicity on the Type

A variable type may include a multiplicity bracket. This is how optional values are declared. A bounded multiplicity above one declares a fixed-size array, such as `var slots: int[3]`. An array of class or feature type, such as `var queue: Job[2] := [first, second]`, holds one instance per slot, and a call through a slot, such as `inline queue[i].finish()`, runs on the instance the slot holds; the validator rejects such an array as a local variable (`OXSTS.UNSUPPORTED_FEATURE`). An array is read and written slot by slot (`slots[i]`), or as a whole: `slots := other`, `slots := [0, 0, 0]`, `havoc(slots)` and `slots == other` act on every slot.

```oxsts
var occupant: Item[0..1] := nothing
```

## Feature-Typed Variables

A variable's type may be a [feature](features.md). The value domain of such a *feature-typed variable* is the set of instances reachable through that feature. A call through the variable, such as `inline active.step()`, runs the call on the instance the variable holds; the validator rejects member access through a variable or an array slot, such as `active.value` (`OXSTS.UNSUPPORTED_FEATURE`).

```oxsts
class Region {
  contains substates: Substate[0..*]

  var active: substates[0..1] := nothing
}
```
