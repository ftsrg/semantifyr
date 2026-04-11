---
title: Transitions
sidebar_position: 10
---

# Transitions

Transitions define **behavior**: how the system changes state. A transition body is a sequence of [operations](operations.md).

## Transition Kinds

| Keyword | Role |
|---------|------|
| `init` | Initialization transition; runs once at startup. |
| `tran` | Step transition; runs on every step. |
| `havoc` | Non-deterministic reset of variables. |

The implicit base class [`Anything`](standard-library.md) provides empty `init`, `tran`, and `havoc` transitions, so any class that does not declare its own inherits these no-ops.

## Declaring a Transition

A transition declaration consists of one of the kind keywords, a name, a parameter list, and a body in `{ }`.

```oxsts
tran increment() {
  value := value + 1
}
```

With parameters:

```oxsts
tran setBy(amount: int) {
  value := value + amount
}
```

Parameters are passed by value when the transition is invoked through an [inline call](inline-operations.md#inline-call).

## The Implicit `main`, `init`, and `havoc`

A transition declared **without** a name has an **implicit name** based on its kind:

| Declaration | Implicit name |
|-------------|---------------|
| `tran { ... }` | `main` |
| `init { ... }` | `init` |
| `havoc { ... }` | `havoc` |

Hence `tran { value := value + 1 }` is equivalent to a transition `tran main() { value := value + 1 }`. The implicit `main` and `init` transitions are the special transitions checked and run by the model checker.

```oxsts
class Counter {
  var value: int := 0

  redefine init {
    value := 0
  }

  redefine tran {
    value := value + 1
  }
}
```

## Multiple Branches with `or`

A transition body may contain alternative branches separated by `or`. This is a shorthand for wrapping the body in a top-level [`choice`](operations.md#choice).

```oxsts
tran step() {
  value := value + 1
} or {
  value := value - 1
}
```

The verifier explores both branches non-deterministically.

## Abstract Transitions

An `abstract` transition has no body. Concrete subclasses must redefine it.

```oxsts
abstract tran fire()
```

## Redefining Transitions

Override an inherited transition of the **same name** with `redefine`.

```oxsts
redefine tran increment() {
  if (value < maximum()) {
    value := value + 1
  }
}
```

The same applies to the implicit transitions inherited from `Anything`: overriding them requires `redefine`.

```oxsts
redefine init {
  value := 0
}
```

```oxsts
redefine tran {
  inline child.main()
}
```
