---
title: Features
slug: /learn/language/reference/features
sidebar_position: 3
description: The features of the Semantifyr Language, the structure of a model, with their kinds, multiplicities, subsets, opposites, redefinition, and bindings.
---

# Features

Features describe the structure of a model: which instances a class contains, and which it refers to. The compiler creates the instances from the features before the model runs ([Instantiation](../../compilation/index.mdx#instantiation)), so they do not change while it runs.

A feature has a kind, a name, and a type, as in `contains lamp: Lamp`.

## `contains`

A `contains` feature owns its instance. Each `Pair` has two counters of its own:

```oxsts
class Counter {
  var value: int := 0
}

class Pair {
  contains first: Counter
  contains second: Counter
}
```

## `refers`

A `refers` feature points to an instance owned somewhere else:

```oxsts
class Counter {
  var value: int := 0
}

class Display {
  refers source: Counter
}
```

## Multiplicities

A multiplicity says how many instances a feature holds. Write it in brackets after the type. It is `[1]` by default. `[0..1]` is at most one, `[0..*]` is any number, and `[2..5]` is two to five. `[]` also has no upper bound, but it cannot be `nothing`.

A bound is an integer, or `*` as the upper end of a range. Bounds are computed at compile time, so they cannot read the state. A bare `[*]` is an error.

## `subsets`

`subsets` says that the instances of a feature are also instances of a more general feature. Declare a feature for each part, and subset a feature that holds them all:

```oxsts
class Item

class Shelf {
  contains items: Item[0..*] // holds first and second
  contains first: Item subsets items
  contains second: Item subsets items
}
```

A feature and its redefinitions hold the same instances, so subsetting one of them subsets them all. A redefinition without its own `subsets` keeps the one of the feature it redefines. No feature can subset a bound feature or a `derived refers`, because their instances come from the binding or the opposite.

## `opposite`

`opposite` links two features that point at each other, so a link can be followed both ways. Each of the two must name the other:

```oxsts issue=OXSTS.INCORRECT_OPPOSITE
class Tree {
  contains children: Tree[0..*] opposite parent
  container parent: Tree[0..1] // an error
}
```

## `container`

A `container` feature points from a child back to the instance that contains it. Pair it with that instance's `contains` through `opposite`:

```oxsts
class Tree {
  contains children: Tree[0..*] opposite parent
  container parent: Tree[0..1] opposite children
}
```

## `derived refers`

A `derived refers` feature collects the instances that refer to this one through its `opposite`. Here, `incoming` holds every edge whose `target` is this node:

```oxsts
class Node {
  derived refers incoming: Edge[0..*] opposite target
}

class Edge {
  refers target: Node opposite incoming
}
```

## `features`

`features` declares a feature and leaves its kind to the subclasses:

```oxsts
class Item

abstract class Holder {
  features content: Item
}

class Box : Holder {
  redefine contains content: Item
}
```

A subclass redefines it as `contains` or `refers`, or declares features that subset it. A concrete class must redefine or subset every `features` it declares or inherits.

## `redefine` and `redefines`

Replace an inherited feature with `redefine`, usually to give it a more specific type ([Redefining Members](classes.md#redefining-members)):

```oxsts
class Item

class SpecialItem : Item

class Box {
  contains item: Item
}

class SpecialBox : Box {
  redefine contains item: SpecialItem
}
```

`redefines` replaces an inherited feature under a new name. Inherited members that use the old name then use the new feature:

```oxsts continue
class RenamedBox : Box {
  contains special: SpecialItem redefines item
}
```

## Feature Binding

Bind a feature to a value with `=`:

```oxsts
class Counter {
  refers maximum: int = 10
  var value: int := 0

  tran increment() {
    if (value < maximum) {
      value := value + 1
    }
  }
}
```

A bound feature is used like a value of its type, as in `value < maximum`. The value is computed at compile time, once for each instance, so it cannot read variables. It can also be an instance, as in `refers source: Counter = counter`. A subclass or a [nested block](#nested-features) can redefine the feature with another value.

## Nested Features

Redefine the features of a feature's own instance in a block after it. A binding in the block can name the other features of the enclosing class:

```oxsts
class Counter

class Display {
  refers source: Counter
}

class Panel {
  contains counter: Counter
  contains display: Display {
    redefine refers source: Counter = counter // the counter of this panel
  }
}
```

The block can also add features that subset the features of the instance.

## Global Features

Declare a feature outside any class with `global containment` or `global reference`. It belongs to the universe, beside the root instance, and every class can use it by name ([Instantiation](../../compilation/index.mdx#instantiation)):

```oxsts
class Event

global containment globalEvent: Event
global reference currentEvent: Event = globalEvent
```
