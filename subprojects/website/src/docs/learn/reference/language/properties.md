---
title: Properties
sidebar_position: 9
---

# Properties

Properties are **pure computations**: they return a value without changing any state. Properties are used for guards, queries, and the verification property.

## Declaring a Property

A property declaration consists of the `prop` keyword, a name, a parameter list, a return type, and a body containing exactly one `return` statement followed by an expression, whose value fits the return type ([Assignability](types.md#assignability), `OXSTS.TYPE_MISMATCH`). Its value is the value of that expression in the state it is read in.

```oxsts
// hide-start
class Counter {
  var value: int := 0

// hide-end
  prop isPositive(): bool {
    return value > 0
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
  prop isAtLeast(threshold: int): bool {
    return value >= threshold
  }
// hide-start
}
// hide-end
```

A parameter may hold an instance, and the body reads its members. A parameter whose lower bound is 0, such as `[0..1]`, may be left out of a call. An argument that is a variable reads the members of the instance the variable holds. Two parameters of one property cannot share a name (`OXSTS.DUPLICATE_NAME`).

```oxsts
// hide-start
class Worker {
  var done: bool := false
}

class Supervisor {
// hide-end
  prop isDoneOf(worker: Worker[0..1]): bool {
    return worker.done
  }
// hide-start
}
// hide-end
```

The single-`return` restriction may be relaxed in future versions.

## Abstract Properties

An `abstract` property has no body. Only an abstract class declares one (`OXSTS.MEMBER_NOT_IN_ABSTRACT_CLASS`), and a concrete class that inherits it redefines it (`OXSTS.ABSTRACT_CLASS_INHERITED_MEMBERS_NOT_REDEFINED`), as [Abstract Classes](classes.md#abstract-classes) gives.

```oxsts
// hide-start
abstract class Limit {
// hide-end
  abstract prop maximum(): int
// hide-start
}
// hide-end
```

## Redefining Properties

Override an inherited property of the **same name** with `redefine`. The redefinition takes as many parameters as the inherited property (`OXSTS.REDEFINITION_SIGNATURE_MISMATCH`), its return type must be assignable to the inherited one (`OXSTS.TYPE_MISMATCH`), and a `redefine` that no inherited property answers is rejected (`OXSTS.REDEFINED_NOT_FOUND`), as [Redefining Members](classes.md#redefining-members) gives.

```oxsts continue
// hide-start
class TenLimit : Limit {
// hide-end
  redefine prop maximum(): int {
    return 10
  }
// hide-start
}
// hide-end
```

## Invoking a Property

A property is invoked via the `()` invocation expression.

```oxsts
// hide-start
class Counter {
  var value: int := 0

  prop maximum(): int {
    return 10
  }

// hide-end
  prop atLimit(): bool {
    return value == maximum()
  }
// hide-start
}
// hide-end
```

```oxsts continue
// hide-start
class Pair {
  contains a: Counter
  contains b: Counter

// hide-end
  prop pairAtLimit(): bool {
    return a.atLimit() && b.atLimit()
  }
// hide-start
}
// hide-end
```

The arguments bind as [Calls and Arguments](expressions.md#calls-and-arguments) gives, and the call's value is the body's with each parameter replaced by its argument.

## The Verification Property

A `prop { return expr; }` without a name has the **implicit name** `prop`. This is the special property checked by the model checker. The body uses [temporal operators](expressions.md#temporal-operators) such as `AG` and `EF` to express safety and reachability properties.

```oxsts
// hide-start
class Counter {
  var value: int := 0
}

// hide-end
class StaysBelow100 {
  contains counter: Counter

  prop {
    return AG counter.value < 100
  }
}
```

When the class is verified, the model checker decides the body over the reachable states: `AG p` holds when `p` holds in every reachable state, `EF p` when it holds in at least one.

The verdict is **Passed** when the property holds and **Failed** when it does not; the verdicts of a verification that decides neither are listed under [Verdicts](../cli/index.mdx#verdicts).

## Properties vs Transitions

|             | Properties | Transitions |
|-------------|------------|-------------|
| Side effects | None (pure) | Modify variables |
| Return value | Yes        | No |
| Used as     | Expressions | Statements |
| Keyword     | `prop`     | `tran`, `init` |
