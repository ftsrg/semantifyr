---
title: Transitions
slug: /learn/language/reference/transitions
sidebar_position: 4
description: The transitions of the Semantifyr Language, how the state changes, with main and init, initial states, branches, and abstract transitions.
---

# Transitions

Transitions change the state. `init` sets up the initial states, and `tran` is the step the model takes again and again. A named transition runs only when another transition calls it with an [inline call](inline-operations.md#inline-call).

## Declaring a Transition

Declare a transition with `tran`, a name, parameters, and a body of [operations](operations.md):

```oxsts
class Counter {
  var value: int := 0

  tran increase(amount: int) {
    value := value + amount
  }
}
```

Parameters work as for [properties](properties.md#parameters), but a transition cannot write them. To change a parameter, copy it into a local variable.

## The Implicit `main` and `init`

A `tran` without a name is called `main`, and `init` is called `init`. Every class inherits an empty `main` and `init` from [`Anything`](classes.md#the-anything-base-class), so declare your own with `redefine`:

```oxsts
class Counter {
  var value: int := 0

  redefine init {
    value := 1
  }

  redefine tran {
    value := value + 1
  }
}
```

A verification case runs its own `init` and `main`, inherited or redefined, but not those of its parts. To run those of a part, call them, as [inlining](../../compilation/index.mdx#inlining) then expands:

```oxsts continue
class Holder {
  contains counter: Counter

  redefine init {
    inline counter.init()
  }

  redefine tran {
    inline counter.main()
  }
}
```

## Initial States

The initial states are the states `init` reaches from the initial values of the variables. Use [`havoc`](operations.md#havoc) in `init` to start from any value:

```oxsts
class Counter {
  var value: int := 0

  redefine init {
    havoc(value) // value starts at any integer
  }
}
```

If `init` [blocks](operations.md#blocking), the model has no states, so every `AG` property holds and no `EF` property does.

## Branches with `or`

Separate branches with `or`. Each run of the transition takes one of the branches that do not block:

```oxsts
class Counter {
  var value: int := 0

  tran step() {
    value := value + 1
  } or {
    value := value - 1
  }
}
```

This works like a [`choice`](operations.md#choice) around the whole body. `init` can have branches too, and each branch gives initial states.

## Abstract Transitions

An abstract transition has no body. Declare one with `abstract` in an [abstract class](classes.md#abstract-classes):

```oxsts
abstract class Switch {
  abstract tran toggle()
}
```
