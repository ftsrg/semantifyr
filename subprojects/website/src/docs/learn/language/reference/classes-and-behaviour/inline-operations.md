---
title: Inline Operations
slug: /learn/language/reference/inline-operations
sidebar_position: 6
description: The inline operations of the Semantifyr Language, expanded by the compiler, the inline call, inline if, inline for, and inline for choice.
---

# Inline Operations

The compiler [expands](../../compilation/index.mdx#inlining) inline operations before the model runs. Their conditions and ranges are computed at compile time, so they cannot read the state. Each instance gets its own result. Use them to make the behaviour of an instance depend on its features.

## Inline Call

Call a transition with `inline`. The call runs the body of the transition in its place. A step of `Pair` increments `a` and then `b`:

```oxsts
class Counter {
  var value: int := 0

  tran increment() {
    value := value + 1
  }
}

class Pair {
  contains a: Counter
  contains b: Counter

  redefine tran {
    inline a.increment()
    inline b.increment()
  }
}
```

If the class of the instance redefines the transition, the call runs the redefinition.

A call with `.` needs exactly one instance. A call with `?.` takes an optional one, and does nothing when there is none:

```oxsts
class Marker {
  var done: bool := false

  tran mark() {
    done := true
  }
}

class Dispatcher {
  contains first: Marker
  var current: Marker[0..1] := nothing

  redefine tran {
    inline current?.mark() // no call while current is nothing
  }
}
```

Any other multiplicity is an error. To call every instance of a feature, use an [`inline for`](#inline-for-sequence).

Arguments are given as for a [property call](properties.md#calling-a-property). An argument that reads the state is read once, before the body runs. After a step of `Swapper`, `x` is 2 and `y` is 1:

```oxsts
class Swapper {
  var x: int := 1
  var y: int := 2

  tran swap(first: int, second: int) {
    x := second
    y := first
  }

  redefine tran {
    inline swap(x, y)
  }
}
```

A constant, a feature, or an instance passed as an argument is substituted into the body, so an `inline if` or an `inline for` in the body can use it. A step of this counter increments `value` three times:

```oxsts
class Counter {
  var value: int := 0

  tran increment(times: int) {
    inline for (index in 1..times) {
      value := value + 1
    }
  }

  redefine tran {
    inline increment(3)
  }
}
```

## `inline if`

`inline if` keeps its body when the condition holds, and its `else` body otherwise. A step of `Counter` increments `value`, but a step of `Frozen` does nothing:

```oxsts
class Counter {
  refers maximum: int = 10
  var value: int := 0

  tran increment() {
    value := value + 1
  }

  redefine tran {
    inline if (maximum > 0) {
      inline increment()
    }
  }
}

class Frozen : Counter {
  redefine refers maximum: int = 0
}
```

## `inline for` (sequence)

`inline for` repeats its body for each instance of a feature, or for each value of a range, one after the other. `inline for seq` means the same. A step of `Counters` increments `first` and then `second`:

```oxsts
class Counter {
  var value: int := 0

  tran increment() {
    value := value + 1
  }
}

class Counters {
  contains counters: Counter[0..*]
  contains first: Counter subsets counters
  contains second: Counter subsets counters

  redefine tran {
    inline for (counter in counters) {
      inline counter.increment()
    }
  }
}
```

The instances come in the order their features are declared. The loop variable is replaced by each instance in turn, so the body can assign the instance's variables, as in `counter.value := 0`.

## `inline for choice`

`inline for choice` makes each copy of the body a branch of a [`choice`](operations.md#choice), so only one of them runs. A step of `Counters` increments either `first` or `second`:

```oxsts
// hide-start
class Counter {
  var value: int := 0

  tran increment() {
    value := value + 1
  }
}

// hide-end
class Counters {
  contains counters: Counter[0..*]
  contains first: Counter subsets counters
  contains second: Counter subsets counters

  redefine tran {
    inline for choice (counter in counters) {
      inline counter.increment()
    }
  }
}
```

## `else` Branch

An `inline for` can have an `else` body, which runs when the range is empty. Without one, an empty range does nothing, even in an `inline for choice`. This step blocks when `counters` is empty:

```oxsts
// hide-start
class Counter {
  var value: int := 0

  tran increment() {
    value := value + 1
  }
}

// hide-end
class Counters {
  contains counters: Counter[0..*]

  redefine tran {
    inline for choice (counter in counters) {
      inline counter.increment()
    } else {
      assume(false)
    }
  }
}
```
