---
title: Expressions
sidebar_position: 4
---

import { Planned } from '@site/src/components/Constructs';
import constructs from '@oxsts-test-models/constructs/constructs.tsv';

# Expressions

Expressions compute values. They appear in [property](properties.md) bodies, on the right-hand side of assignments, in [operation](operations.md) guards, in feature multiplicities, and in feature bindings.

## Literals

| Kind | Example |
|------|---------|
| Boolean | `true`, `false` |
| Integer | `42`, `-1` |
| Real | `3.14`, `1e-3` |
| String | `"hello"` |
| Nothing | `nothing` |
| Infinity | `*` |
| Array | `[a, b, c]`, `[]` |
| Record (see [Records](datatypes.md#records)) | `Point { x = 0, y = 0 }` |

See [Datatypes](datatypes.md) for the corresponding types.

## `self`

Inside a class body, `self` refers to the current class or feature instance. Outside a class, in a global feature or a record, it is rejected (`OXSTS.SELF_OUTSIDE_CLASS`).

```oxsts
// hide-start
class Display {
  tran bind(counter: Counter) {
  }
}

// hide-end
class Counter {
  refers display: Display

  redefine init {
    inline display.bind(self)
  }
}
```

## Operator Precedence

From **lowest** to **highest** precedence:

| Level | Category | Operators |
|-------|----------|-----------|
| 1 | Temporal (top of expression only) | `AG`, `EF` |
| 2 | Boolean OR | `\|\|` |
| 3 | Boolean XOR | `^^` |
| 4 | Boolean AND | `&&` |
| 5 | Comparison | `<`, `<=`, `>`, `>=`, `==`, `!=` |
| 6 | Range | `..`, `..<` |
| 7 | Additive | `+`, `-` |
| 8 | Multiplicative | `*`, `/` |
| 9 | Unary | `!`, unary `+`, unary `-` |
| 10 | Postfix / member | `.`, `?.`, `[ ]`, `( )` |

As in Java, `&&` binds tighter than `^^`, which binds tighter than `||`, and each is left-associative: `a || b && c` parses as `a || (b && c)`.

:::note
Until November 2026 the three boolean operators shared one level and read left to right, so `a || b && c` meant `(a || b) && c`. For one release the validator warns on an expression that mixes them without parentheses and names the reading it has now.
:::

## Boolean Operators

| Operator | Description |
|----------|-------------|
| `&&` | Logical AND |
| `\|\|` | Logical OR |
| `^^` | Logical XOR |
| `!` | Unary NOT |

## Comparison Operators

| Operator | Description |
|----------|-------------|
| `<` | Less than |
| `<=` | Less than or equal |
| `>` | Greater than |
| `>=` | Greater than or equal |
| `==` | Equal |
| `!=` | Not equal |

`<`, `<=`, `>` and `>=` take numeric operands; ordering other values, such as enum literals (`Color::Red < Color::Green`), is rejected (`OXSTS.TYPE_MISMATCH`).

Evaluated at compile time, as in an `inline if` guard, `==` between instance expressions (features, `self`, navigations, `nothing`) compares the sets of instances the two sides denote: they are equal when they hold the same instances, in any order. `nothing` is the empty set, so an empty side equals only another empty side, and `!=` is the negation of `==`.

## Arithmetic Operators

| Operator | Description |
|----------|-------------|
| `+` | Addition (binary) or unary plus |
| `-` | Subtraction (binary) or unary minus |
| `*` | Multiplication |
| `/` | Division |

## Ranges

| Syntax | Description |
|--------|-------------|
| `a..b` | Inclusive range from `a` to `b` |
| `a..<b` | Exclusive range from `a` to `b-1` |

## Member Access (`.`)

The `.` operator names a member of an instance. When the instance is held by a variable, the member of the instance the variable holds is read (see [Feature-Typed Variables](variables.md#feature-typed-variables)).

```oxsts
// hide-start
class Counter {
  var value: int := 0
}

class Holder {
  contains counter: Counter

  prop current(): int {
    return
// hide-end
    counter.value
// hide-start
  }
}
// hide-end
```

### Optional Member Access (`?.`)

`?.` navigates through an optional (`[0..1]`) reference. An `inline` call whose receiver is absent is dropped, and the rest of the transition runs; reading a member through an absent receiver fails compilation.

```oxsts
// hide-start
class Parent {
  tran notify() {
  }
}

class Child {
  refers parent: Parent[0..1]

  redefine tran {
// hide-end
    inline parent?.notify()
// hide-start
  }
}
// hide-end
```

## Indexing (`[]`)

Accesses an array element by index.

```oxsts
// hide-start
class Buffer {
  var items: int[3] := [0, 0, 0]

  prop first(): int {
    return
// hide-end
    items[0]
// hide-start
  }
}
// hide-end
```

## Property Invocation (`()`)

A [property](properties.md) is invoked using the `()` invocation expression.

```oxsts
// hide-start
class Counter {
  var value: int := 0
  refers a: Counter
  refers b: Counter

  prop maximum(): int {
    return 10
  }

// hide-end
  prop atLimit(): bool {
    return value == maximum()
  }

  prop pairAtLimit(): bool {
    return a.atLimit() && b.atLimit()
  }
// hide-start
}
// hide-end
```

## Temporal Operators

Temporal operators denote logical formulae over the possible executions of the system.

| Operator | Meaning |
|----------|---------|
| `AG expr` | Always globally. `expr` holds in **every** reachable state. Use for safety properties. |
| `EF expr` | Exists finally. `expr` holds in **some** reachable state. Use for reachability checks. |

```oxsts
// hide-start
class Counter {
  var value: int := 0
}

@VerificationCase
class Bounded {
  contains counter: Counter

// hide-end
  prop {
    return AG counter.value < 100
  }
// hide-start
}
// hide-end
```

A temporal operator is only allowed as the whole expression a property returns: one in a transition (`flag := AG true`), below another operator or nested in another temporal operator (`AG (EF on)`) is rejected (`OXSTS.MISPLACED_TEMPORAL_OPERATOR`).

<Planned table={constructs} construct="AF, EG, AX, EX temporal operators">

More temporal operators are planned: `AF expr` (`expr` eventually holds on every execution), `EG expr` (`expr` always holds on some execution), `AX expr` and `EX expr` (`expr` holds in every or in some next state). The parser rejects them today; only `AG` and `EF` parse.

```oxsts issue=syntax
// hide-start
class Counter {
  var value: int := 0
}

@VerificationCase
class Eventually {
  contains counter: Counter

// hide-end
  prop {
    return AF counter.value == 100
  }
// hide-start
}
// hide-end
```

</Planned>
