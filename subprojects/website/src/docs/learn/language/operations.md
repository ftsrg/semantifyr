---
title: Operations
sidebar_position: 11
---

# Operations

Operations are the statements inside [transition](transitions.md) bodies. They divide into **runtime operations** (described on this page, passed to the model checker) and **[inline operations](inline-operations.md)** (expanded at compile time during unfolding).

Operations (and whole transitions) are atomic in nature, which means either the whole is executed as one, or not at all.

## Primitive Operations

### Assignment

`lhs := rhs;` updates a variable's value. The left-hand side may be a navigation expression. Only runtime variables can be written, and no loop variable: the validator rejects an assignment or havoc whose target is substituted at compile time, or is the loop variable of a [`for`](#for) or an [`inline for`](inline-operations.md) (`OXSTS.INCORRECT_ASSIGNMENT`).

```oxsts
// hide-start
class Counter {
  var value: int := 0
  var count: int := 0
  var input: int := 0

  prop maximum(): int {
    return 10
  }

  redefine tran {
// hide-end
    value := value + 1
// hide-start
  }
}
// hide-end
```

```oxsts
// hide-start
class Counter {
  var value: int := 0
  var count: int := 0
  var input: int := 0

  prop maximum(): int {
    return 10
  }

  redefine tran {
// hide-end
    self.value := input
// hide-start
  }
}
// hide-end
```

Assignments always execute.

### Havoc

`havoc(reference);` non-deterministically picks a value of the reference's type. Used to model unknown environment inputs, and in `init` to give a variable an arbitrary initial value.

```oxsts
// hide-start
class Counter {
  var value: int := 0
  var count: int := 0
  var input: int := 0

  prop maximum(): int {
    return 10
  }

  redefine tran {
// hide-end
    havoc(input)
// hide-start
  }
}
// hide-end
```

Havocs are special assignments, and always execute.

### Assumption

`assume(expr);` assumption states that the given `expr` expression **MUST** evaluate to true **IF** the operation is executed.

```oxsts
// hide-start
class Counter {
  var value: int := 0
  var count: int := 0
  var input: int := 0

  prop maximum(): int {
    return 10
  }

  redefine tran {
// hide-end
    assume(value > 0)
// hide-start
  }
}
// hide-end
```

Is only executed if `expr` evaluates to _true_.

## Composite Operations

Composites allow specifying complex behavior.

### Sequence

Curly braces group operations into a sequence, executed in order.

```oxsts
// hide-start
class Counter {
  var value: int := 0
  var count: int := 0
  var input: int := 0

  prop maximum(): int {
    return 10
  }

  redefine tran {
// hide-end
    {
      value := value + 1
      count := count + 1
    }
// hide-start
  }
}
// hide-end
```

Only executed if every inner operation is executed.

### Choice

`choice` is non-deterministic selection. The model checker explores every feasible branch.

```oxsts
// hide-start
class Counter {
  var value: int := 0
  var count: int := 0
  var input: int := 0

  prop maximum(): int {
    return 10
  }

  redefine tran {
// hide-end
    choice {
      value := value + 1
    } or {
      value := value - 1
    }
// hide-start
  }
}
// hide-end
```

Only executed if at least one inner operation is executed; in which case the executed operation will be an enabled one.

:::important
This results in the selection of execution paths based on which assumptions evaluate to true, allowing for the definition of complex selection logic in a simple way.
:::

### `if`

A runtime conditional. The guard is evaluated at runtime, and the specific body is executed accordingly.

```oxsts
// hide-start
class Counter {
  var value: int := 0
  var count: int := 0
  var input: int := 0

  prop maximum(): int {
    return 10
  }

  redefine tran {
// hide-end
    if (value < maximum()) {
      value := value + 1
    } else {
      value := 0
    }
// hide-start
  }
}
// hide-end
```

### `for`

A runtime loop over a range. The loop variable takes each value in the range in turn and cannot be written. The expression can be any runtime expression.

```oxsts
// hide-start
class Counter {
  var value: int := 0
  var count: int := 0
  var input: int := 0

  prop maximum(): int {
    return 10
  }

  redefine tran {
// hide-end
    for (i in 1..10) {
      value := value + 1
    }
// hide-start
  }
}
// hide-end
```

### Local Variable

Local variables allow the definition of temporary locals in the operation structure. A local variable may be a fixed-size array, such as `var values: int[3] := [1, 2, 3]`, indexed like a class array. A local variable carries an initializer like every variable. Two local variables of one sequence cannot share a name (`OXSTS.DUPLICATE_NAME`); a nested sequence may reuse an outer name, which it then shadows. A local variable of class or feature type, such as `var picked: Worker := first` or `var picked: workers := first`, holds one instance like a class variable, and a call or a read through it, such as `inline picked.finish()` or `picked.done`, acts on the instance it holds; a feature-typed local holds the instances of the feature on the instance whose transition declares it, and `havoc(picked)` chooses among the instances of its type.

```oxsts
// hide-start
class Counter {
  var value: int := 0
  var count: int := 0
  var input: int := 0

  prop maximum(): int {
    return 10
  }

  redefine tran {
// hide-end
    var choiceTaken: bool := false
    choice {
        choiceTaken := true
    } or {
        choiceTaken := false
    }
    assume(choiceTaken)
// hide-start
  }
}
// hide-end
```

