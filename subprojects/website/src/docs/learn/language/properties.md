---
title: Properties
sidebar_position: 9
---

# Properties

Properties are **pure computations**: they return a value without changing any state. Properties are used for guards, queries, and the verification property.

## Declaring a Property

A property declaration consists of the `prop` keyword, a name, a parameter list, a return type, and a body containing exactly one `return` statement followed by an expression.

```oxsts
prop isPositive(): bool {
  return value > 0
}
```

With parameters:

```oxsts
prop isAtLeast(threshold: int): bool {
  return value >= threshold
}
```

The single-`return` restriction may be relaxed in future versions.

## Abstract Properties

An `abstract` property has no body. Concrete subclasses must redefine it.

```oxsts
abstract prop maximum(): int
```

## Redefining Properties

Override an inherited property of the **same name** with `redefine`. The parameter and return-type signature must match.

```oxsts
redefine prop maximum(): int {
  return 10
}
```

## Invoking a Property

A property is invoked via the `()` invocation expression.

```oxsts
prop atLimit(): bool {
  return value == maximum()
}
```

```oxsts
prop pairAtLimit(): bool {
  return a.atLimit() && b.atLimit()
}
```

See [Expressions / Property Invocation](expressions.md#property-invocation-).

## The Verification Property

A `prop { return expr; }` without a name has the **implicit name** `prop`. This is the special property checked by the model checker. The body uses [temporal operators](expressions.md#temporal-operators) such as `AG` and `EF` to express safety and reachability properties.

```oxsts
class Reaches10 {
  contains counter: Counter

  prop {
    return AG counter.value < 100
  }
}
```

When the class is verified, the model checker checks that the body expression holds in every reachable state.

## Properties vs Transitions

|             | Properties | Transitions |
|-------------|------------|-------------|
| Side effects | None (pure) | Modify variables |
| Return value | Yes        | No |
| Used as     | Expressions | Statements |
| Keyword     | `prop`     | `tran`, `init`, `havoc` |
