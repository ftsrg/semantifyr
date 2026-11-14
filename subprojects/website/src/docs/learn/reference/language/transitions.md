---
title: Transitions
sidebar_position: 10
---

# Transitions

Transitions define **behavior**: how the system changes state. A transition body is a sequence of [operations](operations.md).

## Transition Kinds

| Keyword | Role |
|---------|------|
| `init` | Initialization transition; the states it reaches from the declaration valuation are the initial states. |
| `tran` | Step transition; runs on every step. |

The implicit base class [`Anything`](standard-library.md) provides empty `init` and `tran` transitions, so any class that does not declare its own inherits these no-ops.

The declaration valuation, every variable at its initializer, is not a state: the system starts in the states `init` reaches from it, and an `init` with no enabled execution leaves the system without states ([Initial States](variables.md#initial-states)).

## Declaring a Transition

A transition declaration consists of one of the kind keywords, a name, a parameter list, and a body in `{ }`.

```oxsts
// hide-start
class Counter {
  var value: int := 0

// hide-end
  tran increment() {
    value := value + 1
  }
// hide-start
}
// hide-end
```

With parameters:

```oxsts
// hide-start
class Counter {
  var value: int := 0

// hide-end
  tran setBy(amount: int) {
    value := value + amount
  }
// hide-start
}
// hide-end
```

Parameters are passed by value when the transition is invoked through an [inline call](inline-operations.md#inline-call). A parameter cannot be an array (`OXSTS.UNSUPPORTED_FEATURE`), and two parameters of one transition cannot share a name (`OXSTS.DUPLICATE_NAME`). A parameter may reuse the name of a member of the class, which the body then reaches as `self.name`.

## The Implicit `main` and `init`

A transition declared **without** a name has an **implicit name** based on its kind:

| Declaration | Implicit name |
|-------------|---------------|
| `tran { ... }` | `main` |
| `init { ... }` | `init` |

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
// hide-start
class Counter {
  var value: int := 0

// hide-end
  tran step() {
    value := value + 1
  } or {
    value := value - 1
  }
// hide-start
}
// hide-end
```

The verifier explores both branches non-deterministically.

## Abstract Transitions

An `abstract` transition has no body. Only an abstract class declares one (`OXSTS.MEMBER_NOT_IN_ABSTRACT_CLASS`), and a concrete class that inherits it redefines it (`OXSTS.ABSTRACT_CLASS_INHERITED_MEMBERS_NOT_REDEFINED`), as [Abstract Classes](classes.md#abstract-classes) gives.

```oxsts
// hide-start
abstract class Transition {
// hide-end
  abstract tran fire()
// hide-start
}
// hide-end
```

## Redefining Transitions

Override an inherited transition of the **same name** with `redefine`. The redefinition takes as many parameters as the inherited transition (`OXSTS.REDEFINITION_SIGNATURE_MISMATCH`), and a `redefine` that no inherited transition answers is rejected (`OXSTS.REDEFINED_NOT_FOUND`), as [Redefining Members](classes.md#redefining-members) gives. A call resolves to the redefinition on the instance it names ([Inline Call](inline-operations.md#inline-call)).

```oxsts
// hide-start
class Counter {
  var value: int := 0

  tran increment() {
    value := value + 1
  }
}

class BoundedCounter : Counter {
  prop maximum(): int {
    return 10
  }

// hide-end
  redefine tran increment() {
    if (value < maximum()) {
      value := value + 1
    }
  }
// hide-start
}
// hide-end
```

The implicit transitions inherited from `Anything` are overridden by declaring them, with or without `redefine`: a class has one `main` and one `init`, and a second anonymous transition of a kind in the same class is a duplicate name (`OXSTS.DUPLICATE_NAME`).

```oxsts
// hide-start
class Counter {
  var value: int := 0

// hide-end
  redefine init {
    value := 0
  }
// hide-start
}
// hide-end
```

```oxsts
// hide-start
class Counter {
  var value: int := 0

  redefine tran {
    value := value + 1
  }
}

class Parent {
  contains child: Counter

// hide-end
  redefine tran {
    inline child.main()
  }
// hide-start
}
// hide-end
```
