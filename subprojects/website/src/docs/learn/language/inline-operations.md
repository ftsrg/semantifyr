---
title: Inline Operations
sidebar_position: 12
---

# Inline Operations

Inline operations are resolved at **compile time** during unfolding. They do not exist in the final XSTS model.

## Inline Call

`inline target.transition(args)` substitutes the body of the target transition at the call site, rewriting `self`, contextual member references, and parameters according to the call.

```oxsts
inline counter.increment()
```

### One-step example

```oxsts
class Counter {
  var value: int := 0

  tran increment() {
    value := value + 1
  }
}
```

```oxsts
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

```oxsts
redefine tran {
  a.value := a.value + 1
  b.value := b.value + 1
}
```

## `inline if`

`inline if (guard) { ... } else { ... }` evaluates the guard at compile time and keeps the surviving branch. The other branch is discarded.

```oxsts
inline if (maximum() > 0) {
  inline increment()
}
```

If `maximum()` evaluates to `10` at compile time, the snippet unfolds to:

```oxsts
inline increment()
```

If `maximum()` had evaluated to `0` instead, the entire `inline if` would be removed (replaced with the empty `else` branch).

## `inline for` (sequence)

`inline for [seq] (x in expr) { ... }` unrolls into a **sequence** of body copies, one per value in `expr`. The `seq` keyword is optional: `inline for (x in r)` and `inline for seq (x in r)` are equivalent.

```oxsts
inline for (counter in counters) {
  inline counter.increment()
}
```

If `counters` instantiates two children at compile time, the snippet unfolds to:

```oxsts
{
  inline counters_0.increment()
  inline counters_1.increment()
}
```

## `inline for choice`

`inline for choice (x in expr) { ... }` unrolls into a non-deterministic **choice** rather than a sequence. The `choice` keyword is required.

```oxsts
inline for choice (counter in counters) {
  inline counter.increment()
}
```

With the same two children, the snippet unfolds to:

```oxsts
choice {
  inline counters_0.increment()
} or {
  inline counters_1.increment()
}
```

## `else` Branch

The `else` branch of an `inline for` is taken when the range expression evaluates to the **empty set** at compile time. The same `else` is available for `inline for [seq]`, `inline for choice`, and `inline if`.

```oxsts
inline for choice (counter in counters) {
  inline counter.increment()
} else {
  assume (false)
}
```

## Inline vs Runtime

|  | Inline | Runtime |
|--|--------|---------|
| Decision depends on | Model structure (which features exist, how many children) | Variable values |
| Evaluated when | Compile time, during unfolding | Runtime, by the model checker |
| Constructs | `inline if`, `inline for`, `inline for choice`, `inline call` | [`if`](operations.md#if), [`for`](operations.md#for) |
