---
title: Features
sidebar_position: 8
---

# Features

Features define the **structural relationships** between classes. Unlike [variables](variables.md) (which hold runtime state), features are resolved during instantiation and eliminated during flattening; they do not exist at runtime.

## Feature Kinds

| Kind | Meaning |
|------|---------|
| `contains` | The class **owns** instances of the target type. |
| `refers` | A cross-reference to isntances owned elsewhere. |
| `container` | The reverse of `contains`; must be paired via `opposite`. |
| `derived refers` | A reference whose value is derived from the model structure. |
| `features` | An **abstract** feature that subclasses must redefine as `contains` or `refers`. |

## `contains`

A `contains` feature declares ownership: each instance of the enclosing class owns its own instance of the target type. Children are instantiated together with their parent.

```oxsts
class Pair {
  contains a: Counter
  contains b: Counter
}
```

## `refers`

A `refers` feature is a cross-reference. It points to an instance that lives elsewhere; there is no ownership.

```oxsts
class Display {
  refers source: Counter
}
```

## `container`

A `container` feature is the **reverse** of a `contains` feature: the child pointing back to its owning parent. A `container` must always be paired with the corresponding `contains` via `opposite`. A validation rule enforces this pairing.

```oxsts
class Tree {
  contains children: Tree[0..*] opposite parent
  container parent: Tree[0..1] opposite children
}
```

## `derived refers`

A `derived refers` feature is computed automatically from the model structure. Derived refers features with `opposite` may be derived from their opposite declaration.

```oxsts
class StateNode {
  derived refers inTrans: Transition[0..*] opposite to
  derived refers outTrans: Transition[0..*] opposite from
}
```

## `features`

The `features` keyword declares an **abstract** feature whose kind is left to subclasses. A subclass must redefine it as a concrete `contains` or `refers`.

```oxsts
abstract class Container {
  features payload: any
}
```

```oxsts
class IntContainer : Container {
  redefine contains payload: int
}
```

## Multiplicities

A feature's multiplicity specifies how many target values are permitted. The multiplicity bracket holds a compile-time expression. The default is `[1]`.

| Syntax | Meaning |
|--------|---------|
| `[1]` | Exactly one (default) |
| `[0..1]` | Optional; may be `nothing` |
| `[0..*]` | Any number |
| `[n]` | Exactly `n` (`n` is a compile-time expression) |
| `[lb..ub]` | Between `lb` and `ub` |
| `[]` | Unbounded shorthand |

The infinity literal `*` is allowed in ranges (`0..*`).

## `opposite`

`opposite` declares a bidirectional relationship: navigating in one direction automatically populates the other. A validation rule enforces that opposite features point to each other and that `container`/`contains` pairs match.

```oxsts
class Tree {
  contains children: Tree[0..*] opposite parent
  container parent: Tree[0..1] opposite children
}
```

## `subsets`

`subsets` declares that the values of one feature are also included in another, more general feature.

```oxsts
class Wrapper {
  contains all: Item[0..*]
  contains primary: Item subsets all
}
```

This is the primary way semantic libraries declaratively add instances to a collection defined in a superclass.

## `redefine` and `redefines`

`redefine` overrides an inherited feature of the **same name**, typically with a more specific type or a more constrained body. 

```oxsts
class Counter10Pair : CounterPair {
  redefine contains a: Counter10
  redefine contains b: Counter10
}
```

The `redefines` modifier may be applied to a redefining feature for additional clarity.

```oxsts
class Counter10Pair : CounterPair {
  contains newA: Counter10 redefines a
  contains newB: Counter10 redefines b
}
```

## Nested Features

A feature declaration may include a block of nested feature declarations to redefine inner structure.

```oxsts
contains state: State {
  redefine contains entryAction: SetValueAction
}
```

## Feature Binding

A feature can be bound to a compile-time expression with `=`. The bound value cannot depend on runtime state.

```oxsts
refers maximum: int = 10
```

## Top-Level (Global) Features

Top-level features are declared **outside** any class. They represent shared instances or references that exist at the root of the model. Conceptually, they act as implicit features of the class being instantiated.

| Kind | Meaning |
|------|---------|
| `global containment` | A top-level owned instance. |
| `global reference` | A top-level reference. |

```oxsts
global containment globalEvent: Event[1]
```

Top-level features currently support only the `subsets` modifier. This restriction may be relaxed in future versions.

```oxsts
global containment sharedEvent: Event subsets sharedEvents
```
