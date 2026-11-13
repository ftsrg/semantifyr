---
title: Expressions
sidebar_position: 4
---

import Planned from '@site/src/components/Planned';
import constructs from '@oxsts-test-models/constructs/constructs.tsv';

# Expressions

Expressions compute values. They appear in [property](properties.md) bodies, on the right-hand side of assignments, in [operation](operations.md) guards, in feature multiplicities, and in feature bindings. [Types](types.md) gives the type of each expression and when it fits where it is used.

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

[Lexical Structure](lexical-structure.md#literals) gives the forms of the literals, and [Datatypes](datatypes.md) their types.

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
| 1 | Temporal (top of a property only) | `AG`, `EF` |
| 2 | Boolean OR | `\|\|` |
| 3 | Boolean XOR | `^^` |
| 4 | Boolean AND | `&&` |
| 5 | Comparison | `<`, `<=`, `>`, `>=`, `==`, `!=` |
| 6 | Range | `..`, `..<` |
| 7 | Additive | `+`, `-` |
| 8 | Multiplicative | `*`, `/` |
| 9 | Unary and conditional | `!`, unary `+`, unary `-`, `if ... then ... else` |
| 10 | Cast | `as` |
| 11 | Call | `( )` |
| 12 | Member and index | `.`, `?.`, `[ ]` |

The binary operators are left-associative, and a range takes one `..` or `..<`. The guard and the branches of an if-then-else are whole expressions, so its `else` branch reaches as far to the right as it can: `if c then 1 else 2 + 3` is `if c then 1 else (2 + 3)`. A cast applies to a call or a chain of members and indices, so `-x as int` is `-(x as int)`. A call ends a chain of members and indices: `a.b.f()` calls `f` on `a.b`, and nothing but a cast follows the call.

As in Java, `&&` binds tighter than `^^`, which binds tighter than `||`: `a || b && c` parses as `a || (b && c)`. The validator warns on an operand that is another of the three written without parentheses (`OXSTS.MIXED_BOOLEAN_OPERATORS`) and names the reading.

:::note
Until November 2026 the three boolean operators shared one level and read left to right, so `a || b && c` meant `(a || b) && c`.
:::

```oxsts issue=OXSTS.MIXED_BOOLEAN_OPERATORS
class Switch {
  var up: bool := false
  var down: bool := false
  var locked: bool := false

  prop {
    return AG up || down && !locked
  }
}
```

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

## Calls and Arguments

`f(arguments)` calls a [property](properties.md), and is then an expression whose value is the property's, or a [transition](transitions.md), which only an [inline call](inline-operations.md#inline-call) does. The callee is named like a member, by itself, through `self` or through a navigation, and calling anything else, such as a variable or a record, is rejected (`OXSTS.INCORRECT_CALLED_ELEMENT`).

The same rules bind the arguments of a property call, of an inline call and of an [annotation](annotations.md#with-arguments):

- An argument is positional or named, `name = value`. A positional argument binds the parameter at its position, a named one the parameter of its name, so a call resolved against an inherited declaration binds the parameters of its redefinition by name too.
- Every named argument follows all positional ones (`OXSTS.POSITIONAL_ARGUMENT_AFTER_NAMED`), and a parameter is bound at most once, by position or by name (`OXSTS.PARAMETER_BOUND_TWICE`).
- A parameter whose multiplicity has the lower bound 0, such as `[0..1]`, is optional and binds `nothing` when it is left out. Every other parameter must be bound, and a call has no more arguments than parameters (`OXSTS.INVALID_CALL_ARGUMENTS_COUNT`); a required parameter is declared before every optional one (`OXSTS.INVALID_CALL_ARGUMENTS_COUNT`, at the declaration).
- Each argument fits its parameter's type ([Assignability](types.md#assignability)).

A property call's value is the value of the property's body with every parameter replaced by its argument, read against the instance the call names; an inline call passes its arguments by value ([Inline Call](inline-operations.md#inline-call)).

```oxsts
class Counter {
  var value: int := 0

  prop exceeds(limit: int, margin: int[0..1]): bool {
    return value > limit
  }

  prop atLimit(): bool {
    return !exceeds(10) && exceeds(limit = 9)
  }
}

class Pair {
  contains a: Counter
  contains b: Counter

  prop bothAtLimit(): bool {
    return a.atLimit() && b.atLimit()
  }
}
```

```oxsts issue=OXSTS.PARAMETER_BOUND_TWICE
class Counter {
  var value: int := 0

  prop exceeds(limit: int, margin: int[0..1]): bool {
    return value > limit
  }

  prop atLimit(): bool {
    return exceeds(10, limit = 9)
  }
}
```

```oxsts issue=OXSTS.INCORRECT_CALLED_ELEMENT
record Point {
  var x: int := 0
}

class Plotter {
  var pen: Point := Point()
}
```

## Casts (`as`)

`expression as Type` gives an expression a narrower type, as a promise that its value fits that type. A cast is accepted when the named type fits the expression's own ([Assignability](types.md#assignability)): to a subclass, to a subsetting feature, from `any` to a datatype, to a smaller multiplicity such as from `[0..*]` to `[1]`, or to the same type. A cast that widens, such as from a class to its superclass or to `any`, or one between unrelated types, is rejected (`OXSTS.INVALID_CAST`). A cast changes nothing when the model runs: the compiler checks the same rule again and then drops it, so nothing checks the promise, and the value is used as it is.

```oxsts
abstract class Expression {
  abstract prop evaluate(): any
}

class Guard {
  contains condition: Expression

  prop holds(): bool {
    return condition.evaluate() as bool
  }
}
```

```oxsts issue=OXSTS.INVALID_CAST
class Flag {
  var on: bool := false

  prop wide(): any {
    return on as any
  }
}
```

## If-Then-Else

`if guard then a else b` is `a` when the guard holds and `b` otherwise. The guard is Boolean, the `else` branch is required, and the two branches join ([Comparison and Joining](types.md#comparison-and-joining)); either is rejected otherwise (`OXSTS.TYPE_MISMATCH`). Its type is the joined type of the branches, so `if dark then lamp else nothing` holds at most one instance. Unlike the [`if` operation](operations.md#if), it is an expression and changes no state.

```oxsts
class Lamp

class Room {
  contains lamp: Lamp
  var dark: bool := false
  var level: int := 0
  var lit: Lamp[0..1] := nothing

  redefine tran {
    havoc(dark)
    level := if dark then 100 else 0
    lit := if dark then lamp else nothing
  }
}
```

```oxsts issue=OXSTS.TYPE_MISMATCH
class Room {
  var dark: bool := false
  var level: int := 0

  redefine tran {
    level := if dark then 100 else false
  }
}
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
