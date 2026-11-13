---
title: Features
sidebar_position: 8
---

# Features

Features define the **structural relationships** between classes. Unlike [variables](variables.md) (which hold runtime state), features are resolved during instantiation and eliminated during flattening; they do not exist at runtime.

A feature declaration is a kind, a name, `:` and a type with an optional [multiplicity](#multiplicities), then any of `subsets`, `opposite` and `redefines`, and either a [binding](#feature-binding), a block of [nested features](#nested-features) or nothing. Two members of one class cannot share a name (`OXSTS.DUPLICATE_NAME`). A feature of a record type is rejected (`OXSTS.UNSUPPORTED_FEATURE`); a record value is held by a variable.

## Feature Kinds

| Kind | Meaning |
|------|---------|
| `contains` | The class **owns** instances of the target type. |
| `refers` | A cross-reference to instances owned elsewhere. |
| `container` | The reverse of `contains`; must be paired via `opposite`. |
| `derived refers` | A reference whose value is derived from the model structure. |
| `features` | An **abstract** feature that subclasses redefine as `contains` or `refers`, or fill through subsetters. |

## `contains`

A `contains` feature declares ownership: each instance of the enclosing class owns its own instance of the target type. A `[1]` containment is instantiated together with its holder, one instance per holder instance; a containment of any other multiplicity holds only the instances of the features that [subset](#subsets) it, so `n` children are written as `n` `[1]` subsetters.

```oxsts
// hide-start
class Counter {
  var value: int := 0
}

// hide-end
class Pair {
  contains a: Counter
  contains b: Counter
}
```

## `refers`

A `refers` feature is a cross-reference. It points to an instance that lives elsewhere; there is no ownership.

```oxsts
// hide-start
class Counter {
  var value: int := 0
}

// hide-end
class Display {
  refers source: Counter
}
```

## `container`

A `container` feature is the **reverse** of a `contains` feature: the child pointing back to its owning parent. A `container` must always be paired with the corresponding `contains` via `opposite` (`OXSTS.INCORRECT_OPPOSITE`).

```oxsts
class Tree {
  contains children: Tree[0..*] opposite parent
  container parent: Tree[0..1] opposite children
}
```

## `derived refers`

A `derived refers` feature is computed automatically from the model structure: it holds the instances whose `opposite` feature refers to the holder. A `derived refers` without an `opposite`, declared or inherited, is rejected (`OXSTS.UNSUPPORTED_FEATURE`).

```oxsts
class StateNode {
  derived refers inTrans: Transition[0..*] opposite to
  derived refers outTrans: Transition[0..*] opposite from
}
// hide-start

class Transition {
  refers from: StateNode opposite outTrans
  refers to: StateNode opposite inTrans
}
// hide-end
```

## `features`

The `features` keyword declares an **abstract** feature whose kind is left to subclasses. A subclass redefines it as a concrete `contains` or `refers`, or fills it through features that [subset](#subsets) it: one that is not redefined holds the instances of its subsetters. A concrete class with a `features` that it neither redefines nor subsets is rejected (`OXSTS.ABSTRACT_FEATURE_NOT_REALIZED`).

```oxsts
abstract class Container {
  features payload: any
}
```

```oxsts continue
class IntContainer : Container {
  redefine contains payload: int
}
```

## Multiplicities

A feature's multiplicity specifies how many target values are permitted. The multiplicity bracket holds an expression evaluated at compile time, which cannot read the state (`OXSTS.EXPRESSION_MODALITY_TOO_HIGH`). The default is `[1]`.

| Syntax | Meaning |
|--------|---------|
| `[1]` | Exactly one (default) |
| `[0..1]` | Optional; may be `nothing`, and a parameter with lower bound 0 may be left out of a call |
| `[0..*]` | Any number |
| `[n]` | Exactly `n` (`n` is a compile-time expression) |
| `[lb..ub]` | Between `lb` and `ub` |
| `[]` | No bound given; unlike `[0..*]`, a `[]` parameter is required and a `[]` value cannot be `nothing` |

The infinity literal `*` is allowed in ranges (`0..*`); a bare `[*]` is rejected (`OXSTS.TYPE_MISMATCH`).

## `opposite`

`opposite` declares a bidirectional relationship: an instance is in one feature of another exactly when that other is in the opposite feature of the first, so navigating either way finds the same link. The two features name each other as `opposite`, and the opposite of a `container` is a `contains` and that of a `contains` a `container` (`OXSTS.INCORRECT_OPPOSITE`).

```oxsts
class Tree {
  contains children: Tree[0..*] opposite parent
  container parent: Tree[0..1] opposite children
}
```

```oxsts issue=OXSTS.INCORRECT_OPPOSITE
class Tree {
  contains children: Tree[0..*] opposite parent
  container parent: Tree[0..1]
}
```

## `subsets`

`subsets` declares that the values of one feature are also included in another, more general feature.

```oxsts
// hide-start
class Item

// hide-end
class Wrapper {
  contains all: Item[0..*]
  contains primary: Item subsets all
}
```

This is the primary way semantic libraries declaratively add instances to a collection defined in a superclass. A redefinition that names no `subsets` keeps the superset of the feature it redefines. A bound feature (`= expr`) and a `derived refers` hold the instances their expression or opposite gives them, so neither can be subset (`OXSTS.INCORRECT_SUBSETS`).

## `redefine` and `redefines`

`redefine` overrides an inherited feature of the **same name**, typically with a more specific type or a more constrained body. 

```oxsts
// hide-start
class Counter {
  var value: int := 0
}

class Counter10 : Counter

class CounterPair {
  contains a: Counter
  contains b: Counter
}

// hide-end
class Counter10Pair : CounterPair {
  redefine contains a: Counter10
  redefine contains b: Counter10
}
```

`redefines` replaces an inherited feature under a new name: the old name no longer resolves on the subclass, and inherited members that use it reach the new feature. A class redefines an inherited feature at most once, by `redefine` or by `redefines` (`OXSTS.DUPLICATE_REDEFINITION`).

```oxsts
// hide-start
class Counter {
  var value: int := 0
}

class Counter10 : Counter

class CounterPair {
  contains a: Counter
  contains b: Counter
}

// hide-end
class Counter10Pair : CounterPair {
  contains newA: Counter10 redefines a
  contains newB: Counter10 redefines b
}
```

## Nested Features

A feature declaration may include a block of nested feature declarations to redefine inner structure. Two nested features of one block cannot share a name (`OXSTS.DUPLICATE_NAME`), and they redefine an inherited feature at most once (`OXSTS.DUPLICATE_REDEFINITION`).

```oxsts
// hide-start
abstract class Action

class SetValueAction : Action

class State {
  contains entryAction: Action
}

class Machine {
// hide-end
  contains state: State {
    redefine contains entryAction: SetValueAction
  }
// hide-start
}
// hide-end
```

## Feature Binding

A feature can be bound to a compile-time expression with `=`, whose value fits the feature's type (`OXSTS.TYPE_MISMATCH`). The bound value cannot depend on runtime state (`OXSTS.EXPRESSION_MODALITY_TOO_HIGH`). A bound feature holds the instances or the value its expression gives, on each instance of its class.

```oxsts
// hide-start
class Limit {
// hide-end
  refers maximum: int = 10
// hide-start
}
// hide-end
```

## Top-Level (Global) Features

Top-level features are declared **outside** any class. They represent shared instances or references that exist at the root of the model. Conceptually, they act as implicit features of the class being instantiated.

| Kind | Meaning |
|------|---------|
| `global containment` | A top-level owned instance. |
| `global reference` | A top-level reference. |

```oxsts
// hide-start
class Event

// hide-end
global containment globalEvent: Event[1]
global reference currentEvent: Event = globalEvent
```

Top-level features currently support only the `subsets` modifier. This restriction may be relaxed in future versions.

```oxsts
// hide-start
class Event

global containment sharedEvents: Event[0..*]

// hide-end
global containment sharedEvent: Event subsets sharedEvents
```
