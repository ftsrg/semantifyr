---
title: Operations
sidebar_position: 11
---

# Operations

Operations are the statements inside [transition](transitions.md) bodies. They divide into **runtime operations** (described on this page, passed to the model checker) and **[inline operations](inline-operations.md)** (expanded at compile time during unfolding).

Operations (and whole transitions) are atomic in nature, which means either the whole is executed as one, or not at all.

## Primitive Operations

### Assignment

`lhs := rhs;` updates a variable's value. The left-hand side may be a navigation expression. Only runtime variables can be written: the validator rejects an assignment or havoc whose target is substituted at compile time, such as the loop variable of an [`inline for`](inline-operations.md) (`OXSTS.INCORRECT_ASSIGNMENT`).

```oxsts
value := value + 1
```

```oxsts
self.value := input
```

Assignments always execute.

### Havoc

`havoc(reference);` non-deterministically picks a value of the reference's type. Used to model unknown environment inputs.

```oxsts
havoc (input)
```

Havocs are special assignments, and always execute.

### Assumption

`assume(expr);` assumption states that the given `expr` expression **MUST** evaluate to true **IF** the operation is executed.

```oxsts
assume (value > 0)
```

Is only executed if `expr` evaluates to _true_.

## Composite Operations

Composites allow specifying complex behavior.

### Sequence

Curly braces group operations into a sequence, executed in order.

```oxsts
{
  value := value + 1
  count := count + 1
}
```

Only executed if every inner operation is executed.

### Choice

`choice` is non-deterministic selection. The model checker explores every feasible branch.

```oxsts
choice {
  value := value + 1
} or {
  value := value - 1
}
```

Only executed if at least one inner operation is executed; in which case the executed operation will be an enabled one.

:::important
This results in the selection of execution paths based on which assumptions evaluate to true, allowing for the definition of complex selection logic in a simple way.
:::

### `if`

A runtime conditional. The guard is evaluated at runtime, and the specific body is executed accordingly.

```oxsts
if (value < maximum()) {
  value := value + 1
} else {
  value := 0
}
```

### `for`

A runtime loop over a range. The loop variable takes each value in the range in turn. The expression can be any runtime expression. Only the Theta backend supports runtime `for`, and only over a range; the other backends report the case as not supported.

```oxsts
for (i in 1..10) {
  value := value + 1
}
```

### Local Variable

Local variables allow the definition of temporary locals in the operation structure. A local variable may be a fixed-size array, such as `var values: int[3] := [1, 2, 3]`, indexed like a class array. A local variable without an initializer is havoced each time its declaration runs.

```oxsts
var choiceTaken: bool := false
choice {
    choiceTaken := true
} or {
    choiceTaken := false
}
assume (choiceTaken)
```

