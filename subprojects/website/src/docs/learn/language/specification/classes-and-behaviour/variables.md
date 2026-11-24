---
title: Variables
slug: /learn/language/specification/variables
sidebar_position: 7
description: The variables of the Semantifyr Language, the mutable state of a model, their declaration, initial states, multiplicity on the type and feature-typed variables.
---

# Variables

Variables hold the **mutable state** of the system. Unlike [features](features.md), which define structure and are eliminated during flattening, variables persist across [transitions](transitions.md) and are tracked by the model checker; a variable of type `clock` also advances with the time that passes before each main step ([Time](time.md)).

## Variable Declaration 

A variable declaration consists of the `var` keyword, a name, a type and an initializer. Both are required: the validator rejects a variable without a type and one without an initializer. The loop variable of a `for` and the parameters of a transition or property take their values from the range and the arguments and have no initializer. The initializer's value fits the variable's type ([Assignability](../foundations/types.md#assignability)). {/* OXSTS.VARIABLE_WITH_IMPLICIT_TYPE */} {/* OXSTS.MISSING_INITIALIZER */}

```oxsts
// hide-start
class Counter {
// hide-end
  var value: int := 0
// hide-start
}
// hide-end
```

The initializer of a variable of a class or a record is evaluated when the model is compiled, once for each instance, and cannot read the state, such as another variable; it may read constants and features, such as `refers limit: int = 10`. A [local variable](operations.md#local-variable)'s initializer is evaluated where the variable is declared, and may read the state.

```oxsts issue=OXSTS.EXPRESSION_MODALITY_TOO_HIGH
class Counter {
  var value: int := 0
  var previous: int := value
}
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

A variable type may include a multiplicity bracket. This is how optional values are declared. A bounded multiplicity above one declares a fixed-size array, such as `var slots: int[3] := [0, 0, 0]`, whose initializer gives every slot. An array of class or feature type, such as `var queue: Job[2] := [first, second]`, holds one instance per slot, and a call or a read through a slot, such as `inline queue[i].finish()` or `queue[i].done`, acts on the instance the slot holds. An array is read and written slot by slot (`slots[i]`), an index outside the array blocking the operation that evaluates it, or as a whole: `slots := other`, `slots := [0, 0, 0]`, `havoc(slots)` and `slots == other` act on every slot. An array's upper bound is known at compile time: a variable of multiplicity `[]`, `[*]` or `[n..*]` is rejected, as is an array literal initializer with fewer values than the lower bound or more than the upper bound; one with at least the lower bound and fewer than the upper bound, such as two values for `int[2..3]`, leaves a slot without a value.

The initial value of an array may end in `...` after at least one value: the last written value fills the remaining slots, so `[0, ...]` gives every slot 0 and `[1, 2, 0, ...]` writes a prefix. A literal ending in `...` takes its length from the variable it initializes and is rejected anywhere else, in an assignment or a comparison among others, and so is a prefix longer than a size written as a number.

The size of a class-level array may come from a binding of its class, such as `int[capacity]` or `Item[0..capacity]` beside `refers capacity: int`. Each bound is evaluated on the instance, so every instance holds as many slots as its own binding gives, and such an array takes an index at one slot as at any other. A lower bound of 0 admits `nothing` in a slot. A bound that reads the state is rejected, and so is a size from a binding on a local variable. What depends on the size is checked when an instance is compiled, and the error names the instance: a literal or an array of another length assigned or compared, an initial value longer than the size, a constant index outside it, and a size below none. At a size of none the variable has no storage: the declaration with its initial value stands, and any other use is an error for that instance, so a class whose size may be none guards each use with an `inline if` on the size, or, in a property, with an [if-then-else](../foundations/expressions.md#if-then-else) on it: `return if capacity > 0 then items[0] == item else false`.

```oxsts
class Item

class Queue {
  refers capacity: int = 2
  var values: int[capacity] := [0, ...]
  var items: Item[0..capacity] := [nothing, ...]
  var size: int := 0

  tran put(value: int) {
    inline if (capacity > 0) {
      values[size] := value
    }
    size := size + 1
  }
}
```

```oxsts
// hide-start
class Item

class Slot {
// hide-end
  var occupant: Item[0..1] := nothing
// hide-start
}
// hide-end
```

## Feature-Typed Variables

A variable's type may be a [feature](features.md). The value domain of such a *feature-typed variable* is the set of instances reachable through that feature. A call through the variable, such as `inline active?.step()`, runs the call on the instance the variable holds and is dropped when it holds `nothing`, and a read through it, such as `active.value` or `active.child.value`, reads the member of that instance and blocks the operation that evaluates it when the variable holds `nothing`. The same holds for a variable of class type, whose value domain is every instance of the class, for an array slot (`queue[i].value`), a local variable and the loop variable of a runtime `for`. A write through a variable, such as `active.value := 1` or `havoc(active.value)`, and optional navigation through one in a read or a property call, such as `active?.value`, are rejected.

```oxsts
// hide-start
class Substate

// hide-end
class Region {
  contains substates: Substate[0..*]

  var active: substates[0..1] := nothing
}
```
