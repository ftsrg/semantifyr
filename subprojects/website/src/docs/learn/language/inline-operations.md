---
title: Inline Operations
sidebar_position: 12
---

# Inline Operations

Inline operations are resolved at **compile time** during unfolding. They never reach the model checker.

## Inline Call

`inline target.transition(arguments)` runs the body of the called transition in place of the call, with `self` and the members the body names taken on the instance the call names. The transition is resolved on that instance, so a redefinition in its class is the one that runs. Inlining a call into a body that holds another call goes on until none is left, and nothing stops a transition that calls itself: a library keeps its calls finite.

The target is reached through `self`, a variable, or a feature that holds at most one instance; a call through a feature whose upper multiplicity is above 1 (`inline workers.step()` on `contains workers: Worker[0..*]`) is rejected (`OXSTS.CALL_THROUGH_MANY_VALUED_FEATURE`), so call each instance in an `inline for` over the feature.

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

A step of `Pair` increments the `value` of `a` and then that of `b`.

The arguments bind as [Calls and Arguments](expressions.md#calls-and-arguments) gives, and are passed by value. An argument that reads runtime state (a variable, or a property call over one) is evaluated once, in argument order, into a local variable before the body runs, and the parameter reads that local: a write in the body does not change what a parameter reads. A compile-time argument (a constant, a feature, an instance reference) is substituted for the parameter, so it can steer an `inline if`, an `inline for` range or a call through the parameter. A parameter that holds an instance can be read and called through (`worker.done`, `inline worker.step()`) whether its argument is an instance reference or a variable, in which case the body acts on the instance the variable holds.

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

After the main transition runs, `x` is `2` and `y` is `1`: `first` reads the value `x` had at the call, not the value the body wrote.

A body never writes a parameter: an assignment or a `havoc` whose target is a parameter is rejected (`OXSTS.INCORRECT_ASSIGNMENT`), since arguments are passed by value and the write could not reach the caller. Copy the parameter into a local variable to change it.

## `inline if`

`inline if (guard) { ... } else { ... }` evaluates its guard at compile time, for each instance whose transition holds it, and keeps the body of the branch the guard selects in its place; the other branch is dropped, and without an `else`, a false guard leaves nothing. The guard is Boolean (`OXSTS.TYPE_MISMATCH`) and reads no state (`OXSTS.EXPRESSION_MODALITY_TOO_HIGH`).

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

A step of `Counter` increments `value`; in `Frozen`, whose `maximum` is 0, the step does nothing.

## `inline for` (sequence)

`inline for [seq] (x in expr) { ... }` evaluates its range at compile time, for each instance whose transition holds it, and runs its body once for each value or instance of the range, in order, as one sequence, with `x` standing for that value or instance. The `seq` keyword is optional: `inline for (x in r)` and `inline for seq (x in r)` are equivalent. The range reads no state (`OXSTS.EXPRESSION_MODALITY_TOO_HIGH`), and is what the range of a [`for`](operations.md#for) may be: an integer range, an expression of instances or one of several values (`OXSTS.TYPE_MISMATCH`). Its loop variable cannot be written ([Inline vs Runtime](#inline-vs-runtime)).

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
    inline for seq (counter in counters) {
      inline counter.increment()
    }
  }
}
```

The instances of `counters` are those of the features that subset it, so a step of `Counters` increments the `value` of both `first` and `second`, in the children's declaration order.

## `inline for choice`

`inline for choice (x in expr) { ... }` is evaluated as `inline for` is, but its copies of the body are the branches of a [`choice`](operations.md#choice) rather than a sequence: one runs, chosen non-deterministically among the enabled ones. The `choice` keyword is required.

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
    inline for choice (counter in counters) {
      inline counter.increment()
    }
  }
}
```

A step of `Counters` increments either `first` or `second`.

## `else` Branch

The `else` branch of an `inline for` is taken when the range expression evaluates to the **empty set** at compile time, and that of an `inline if` when the guard is false. The same `else` is available for `inline for [seq]`, `inline for choice`, and `inline if`. Below, a `Counters` without counters has no step at all, since `assume(false)` enables no execution.

```oxsts
// hide-start
class Counter {
  var value: int := 0

  tran increment() {
    value := value + 1
  }
}

class Counters {
  contains counters: Counter[0..*]

  redefine tran {
// hide-end
    inline for choice (counter in counters) {
      inline counter.increment()
    } else {
      assume(false)
    }
// hide-start
  }
}
// hide-end
```

## Inline vs Runtime

|  | Inline | Runtime |
|--|--------|---------|
| Decision depends on | Model structure (which features exist, how many children) | Variable values |
| Evaluated when | Compile time, during unfolding | Runtime, by the model checker |
| Constructs | `inline if`, `inline for`, `inline for choice`, `inline call` | [`if`](operations.md#if), [`for`](operations.md#for) |

The validator hints at an `if` whose guard, or a `for` whose range, is evaluable at compile time (`OXSTS.COULD_BE_INLINE`), and the editor offers a "Make inline" quick fix that turns it into its `inline` form. It also hints at a constant expression that is not already a literal or a reference, such as `2 * 3 + 1` (`OXSTS.CONSTANT_EXPRESSION`), and offers "Inline as constant", which replaces the expression by its value. Neither hint is shown in an inlined model.

The loop variable of a `for` or an `inline for` takes its values from the range and cannot be written: the validator rejects an assignment or a `havoc` whose target is a loop variable (`OXSTS.INCORRECT_ASSIGNMENT`).
