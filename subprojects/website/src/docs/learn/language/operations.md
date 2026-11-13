---
title: Operations
sidebar_position: 11
---

# Operations

Operations are the statements inside [transition](transitions.md) bodies. They divide into **runtime operations** (described on this page, passed to the model checker) and **[inline operations](inline-operations.md)** (expanded at compile time during unfolding).

Operations (and whole transitions) are atomic in nature, which means either the whole is executed as one, or not at all.

## Primitive Operations

### Assignment

`lhs := rhs;` updates a variable's value. The left-hand side may be a navigation expression, an array slot or a record field. Only a variable can be written, and no loop variable or parameter: the validator rejects an assignment or havoc whose target is a feature or another declaration, is substituted at compile time, or is the loop variable of a [`for`](#for) or an [`inline for`](inline-operations.md) (`OXSTS.INCORRECT_ASSIGNMENT`). The value fits the variable's type ([Assignability](types.md#assignability), `OXSTS.TYPE_MISMATCH`).

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

```oxsts issue=OXSTS.INCORRECT_ASSIGNMENT
class Counter {
  refers limit: int = 10

  redefine tran {
    limit := 11
  }
}
```

```oxsts issue=OXSTS.TYPE_MISMATCH
class Counter {
  var value: int := 0

  redefine tran {
    value := true
  }
}
```

### Havoc

`havoc(reference);` non-deterministically picks a value of the reference's type: any `int` or `bool`, any literal of an enumeration, any instance a [feature-typed variable](variables.md#feature-typed-variables) can hold, every field of a record and every slot of an array. Used to model unknown environment inputs, and in `init` to give a variable an arbitrary initial value. Its target is a variable, as an assignment's is (`OXSTS.INCORRECT_ASSIGNMENT`).

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

`assume(expr);` lets an execution go on only where its Boolean condition holds (`OXSTS.TYPE_MISMATCH` for a condition that is not Boolean): where it does not hold, the branch the `assume` is on has no execution, and when every branch of a step is so, the step cannot be taken from that state.

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

```oxsts issue=OXSTS.TYPE_MISMATCH
class Counter {
  var value: int := 0

  redefine tran {
    assume(value)
  }
}
```

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

A runtime conditional. The guard is Boolean (`OXSTS.TYPE_MISMATCH`) and evaluated at runtime: the first body runs where it holds, the `else` body where it does not, and without an `else` nothing happens then. A guard known at compile time could be an [`inline if`](inline-operations.md#inline-if) (`OXSTS.COULD_BE_INLINE`).

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

A runtime loop. Its range is an integer range, such as `1..count`, whose bounds may read the state, an expression of instances, such as a feature, or an expression of several values, such as an array; a single value of a datatype or an enumeration, such as an `int` variable, is rejected (`OXSTS.TYPE_MISMATCH`). Over a range the loop variable takes each integer of the range in turn, and over a feature it holds each instance, so that a call or a member read through it acts on that instance; it cannot be written. A `for` whose range is known at compile time can be an [`inline for`](inline-operations.md#inline-for-sequence), which the validator hints at (`OXSTS.COULD_BE_INLINE`). Not every backend runs a runtime `for`: the pages of [Theta](../backends/theta.mdx), [nuXmv](../backends/nuxmv.mdx), [Spin](../backends/spin.mdx) and [Uppaal](../backends/uppaal.mdx) say which refuse it.

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
    for (i in 1..count) {
      value := value + 1
    }
// hide-start
  }
}
// hide-end
```

### Local Variable

Local variables allow the definition of temporary locals in the operation structure. A local variable may be a fixed-size array, such as `var values: int[3] := [1, 2, 3]`, indexed like a class array. A local variable carries a type and an initializer like every [variable](variables.md#variable-declaration), and its initializer may read the state. Two local variables of one sequence cannot share a name (`OXSTS.DUPLICATE_NAME`); a nested sequence may reuse an outer name, which it then shadows. A local variable of class or feature type, such as `var picked: Worker := first` or `var picked: workers := first`, holds one instance like a class variable, and a call or a read through it, such as `inline picked.finish()` or `picked.done`, acts on the instance it holds; a feature-typed local holds the instances of the feature on the instance whose transition declares it, and `havoc(picked)` chooses among the instances of its type.

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

