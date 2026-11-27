---
title: Variables
slug: /learn/language/reference/variables
sidebar_position: 2
description: The variables of the Semantifyr Language, the state of a model, their declaration, optional variables, arrays, and feature-typed variables.
---

import Planned from '@site/src/components/Planned';

# Variables

Variables hold the state of a model. [Transitions](transitions.md) change them from step to step.

## Declaring a Variable

Declare a variable with `var`, a name, a type, and an initial value:

```oxsts
class Counter {
  var value: int := 0
}
```

The initial value is computed at compile time. It can use constants and [feature bindings](features.md#feature-binding), but not other variables:

```oxsts issue=OXSTS.EXPRESSION_MODALITY_TOO_HIGH
class Counter {
  var value: int := 0
  var previous: int := value // an error
}
```

To start from any value, [havoc](transitions.md#initial-states) the variable in `init`.

## Optional Variables

A variable of multiplicity `[0..1]` is optional. It holds a value or `nothing`:

```oxsts
class Item

class Slot {
  var occupant: Item[0..1] := nothing
}
```

## Arrays

A variable whose multiplicity is a number above one is an array, as in `int[3]`. Write its initial values in brackets, and end the list with `...` to repeat the last value:

```oxsts
class Buffer {
  var items: int[3] := [1, 0, ...] // [1, 0, 0]
  var first: int := 0

  tran read() {
    first := items[0]
    items[2] := 5
  }
}
```

`items[i]` is the element at index `i`, counting from 0. A constant index outside the array is an error. Any other index outside the array makes the operation [block](operations.md#blocking). You can also assign, compare, and havoc a whole array.

The length can be a feature binding, so that each instance gets its own length. When the length can be 0, guard each use of an element with an `inline if`:

```oxsts
class Queue {
  refers capacity: int = 2
  var values: int[capacity] := [0, ...]

  tran put(value: int) {
    inline if (capacity > 0) {
      values[0] := value
    }
  }
}
```

## Feature-Typed Variables

A variable can hold one of the instances of a feature. Write the feature's name as its type. A read or a call through the variable goes to the instance it holds, and [inlining](../../compilation/index.mdx#inlining) turns such a call into a choice over those instances:

```oxsts
class Lamp {
  var lit: bool := false

  tran toggle() {
    lit := !lit
  }
}

class Room {
  contains lamps: Lamp[0..*]
  contains left: Lamp subsets lamps
  contains right: Lamp subsets lamps
  var selected: lamps := left

  tran toggleSelected() {
    inline selected.toggle() // toggles left or right, whichever selected holds
  }
}
```

A variable of class type, such as `Lamp[0..1]`, can hold any instance of the class.

<Planned>

Writing a member through a variable, as in `selected.lit := true`, is not supported yet.

</Planned>
