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

A variable type may include a multiplicity bracket. This is how optional values are declared.

```oxsts
var occupant: Item[0..1] := nothing
```

## Feature-Typed Variables

A variable's type may be a [feature](features.md). The value domain of such a *feature-typed variable* is the set of instances reachable through that feature.

```oxsts
class Region {
  contains substates: Substate[0..*]

  var active: substates[0..1] := nothing
}
```
