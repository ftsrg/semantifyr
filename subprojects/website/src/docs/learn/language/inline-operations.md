---
title: Inline Operations
sidebar_position: 12
---

# Inline Operations

Inline operations are resolved at **compile time** during unfolding. They never reach the model checker.

## Inline Call

`inline target.transition(args)` substitutes the body of the target transition at the call site, rewriting `self` and contextual member references according to the call.

Arguments are passed by value. An argument that reads runtime state (a variable, or a property call over one) is evaluated once, in argument order, into a local variable before the body runs, and the parameter reads that local: a write in the body does not change what a parameter reads. A compile-time argument (a constant, a feature, an instance reference) is substituted for the parameter, so it can steer an `inline if`, an `inline for` range or a call through the parameter.

```oxsts
package example

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

After the main transition runs, `x` is `2` and `y` is `1`: `first` reads the value `x` had at the call, not the value the body wrote.

```oxsts fragment
inline counter.increment()
```

A body never writes a parameter: an assignment or a `havoc` whose target is a parameter is rejected (`OXSTS.INCORRECT_ASSIGNMENT`), since arguments are passed by value and the write could not reach the caller. Copy the parameter into a local variable to change it.

### One-step example

```oxsts
package example

class Counter {
  var value: int := 0

  tran increment() {
    value := value + 1
  }
}
```

```oxsts fragment
class Pair {
  contains a: Counter
  contains b: Counter

  redefine tran {
    inline a.increment()
    inline b.increment()
  }
}
```

After one inlining step the body of `Pair`'s implicit `main` becomes:

```oxsts fragment
redefine tran {
  a.value := a.value + 1
  b.value := b.value + 1
}
```

## `inline if`

`inline if (guard) { ... } else { ... }` evaluates the guard at compile time and keeps the surviving branch. The other branch is discarded.

```oxsts
package example

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
```

Since `maximum` is bound to `10`, the `inline if` unfolds to:

```oxsts fragment
inline increment()
```

If `maximum` were bound to `0` instead, the entire `inline if` would be removed (replaced with the empty `else` branch).

## `inline for` (sequence)

`inline for [seq] (x in expr) { ... }` unrolls into a **sequence** of body copies, one per value in `expr`. The `seq` keyword is optional: `inline for (x in r)` and `inline for seq (x in r)` are equivalent.

```oxsts fragment
inline for (counter in counters) {
  inline counter.increment()
}
```

If `counters` instantiates two children at compile time, the snippet unfolds to:

```oxsts fragment
{
  inline counters_0.increment()
  inline counters_1.increment()
}
```

## `inline for choice`

`inline for choice (x in expr) { ... }` unrolls into a non-deterministic **choice** rather than a sequence. The `choice` keyword is required.

```oxsts fragment
inline for choice (counter in counters) {
  inline counter.increment()
}
```

With the same two children, the snippet unfolds to:

```oxsts fragment
choice {
  inline counters_0.increment()
} or {
  inline counters_1.increment()
}
```

## `else` Branch

The `else` branch of an `inline for` is taken when the range expression evaluates to the **empty set** at compile time. The same `else` is available for `inline for [seq]`, `inline for choice`, and `inline if`.

```oxsts fragment
inline for choice (counter in counters) {
  inline counter.increment()
} else {
  assume(false)
}
```

## Inline vs Runtime

|  | Inline | Runtime |
|--|--------|---------|
| Decision depends on | Model structure (which features exist, how many children) | Variable values |
| Evaluated when | Compile time, during unfolding | Runtime, by the model checker |
| Constructs | `inline if`, `inline for`, `inline for choice`, `inline call` | [`if`](operations.md#if), [`for`](operations.md#for) |

The validator hints at an `if` whose guard, or a `for` whose range, is evaluable at compile time, and the editor offers a "Make inline" quick fix that turns it into its `inline` form.
