---
title: Operations
slug: /learn/language/reference/operations
sidebar_position: 5
description: The operations of the transition bodies of the Semantifyr Language, assignment, havoc, assumption, local variables, sequence, choice, if, if-then-else, for, and blocking.
---

# Operations

Operations are the statements in the body of a [transition](transitions.md). They run as the model runs, unlike [inline operations](inline-operations.md), which the compiler expands beforehand.

## Primitive Operations

### Assignment

Assign a value to a variable with `:=`:

```oxsts
class Counter {
  var value: int := 0

  redefine tran {
    value := value + 1
  }
}
```

You can also assign a variable of another instance through a feature, as in `counter.value := 0`.

Only variables can be assigned, not features, parameters, or loop variables:

```oxsts issue=OXSTS.INCORRECT_ASSIGNMENT
class Counter {
  refers limit: int = 10

  redefine tran {
    limit := 11 // an error
  }
}
```

### Havoc

`havoc(x)` gives the variable `x` an arbitrary value of its type. Use it for inputs from the environment, such as a sensor reading:

```oxsts
class Sensor {
  var reading: int := 0

  redefine tran {
    havoc(reading) // any int
  }
}
```

When `x` is optional, the value can also be `nothing`.

### Assumption

`assume(c)` [blocks](#blocking) when `c` is false. This counter stops at 10, because its step blocks there:

```oxsts
class Counter {
  var value: int := 0

  redefine tran {
    assume(value < 10)
    value := value + 1
  }
}
```

### Local Variable

Declare a local variable with `var` inside a transition. Unlike a variable of a class, its initial value can read the state:

```oxsts
class Counter {
  var value: int := 0

  redefine tran {
    var doubled: int := value * 2
    value := doubled
  }
}
```

A local variable is not part of the state. It exists until the end of the sequence that declares it.

## Composite Operations

### Sequence

Braces group operations into a sequence, which runs them in order. Each operation reads what the ones before it wrote:

```oxsts
class Counter {
  var value: int := 0
  var copy: int := 0

  redefine tran {
    value := value + 1
    copy := value // the new value
  }
}
```

The body of a transition is a sequence, and so are the branches of `choice` and the bodies of `if` and `for`.

### Choice

`choice` runs one of its branches, chosen freely among those that do not block. This counter counts up or down, but stays between 0 and 10:

```oxsts
class Counter {
  var value: int := 0

  redefine tran {
    choice {
      assume(value < 10)
      value := value + 1
    } or {
      assume(value > 0)
      value := value - 1
    }
  }
}
```

When every branch blocks, the `choice` blocks too.

### `if`

`if` runs its body when the condition holds, and the `else` body otherwise:

```oxsts
class Counter {
  var value: int := 0

  redefine tran {
    if (value < 10) {
      value := value + 1
    } else {
      value := 0
    }
  }
}
```

There is no `else if`. To test another condition, put an `if` in the `else` body.

### If-Then-Else

`if c then a else b` is the expression form of `if`. Its value is `a` when `c` holds, and `b` otherwise:

```oxsts
class Room {
  var dark: bool := false
  var level: int := 0

  redefine tran {
    level := if dark then 100 else 0
  }
}
```

The `else` branch is required. When the compiler can decide `c`, it keeps only the branch taken. When `c` reads the state, both branches are evaluated, so `if divisor != 0 then total / divisor else 0` still blocks when `divisor` is 0.

### `for`

`for` runs its body once for each value of a range, or once for each instance of a feature:

```oxsts
class Counter {
  var value: int := 0
  var count: int := 0

  redefine tran {
    for (index in 1..count) {
      value := value + index
    }
  }
}
```

Unlike the range of an `inline for`, this range can read the state. It is read once, when the loop starts.

## Blocking

An operation blocks when it cannot run. The step then cannot take the branch that operation is on. None of the operations on that branch take effect, not even the earlier ones.

An `assume` blocks when its condition is false. Any operation blocks when it evaluates an expression that has no value, such as a division by zero, an index outside an array, or a read through a variable that holds `nothing`. If the step blocks on every branch, the model has no next state.

`&&` and `||` evaluate both operands, so `assume(divisor != 0 && total / divisor > 1)` still blocks when `divisor` is 0.
