---
title: Expressions
slug: /learn/language/specification/expressions
sidebar_position: 4
description: The expressions of the Semantifyr Language, their literals, operators and precedence, casts, if-then-else and calls.
---

import Planned from '@site/src/components/Planned';
import constructs from '@oxsts-constructs/constructs.tsv';

# Expressions

Expressions compute values. They appear in [property](../classes-and-behaviour/properties.md) bodies, on the right-hand side of assignments, in [operation](../classes-and-behaviour/operations.md) guards, in feature multiplicities, and in feature bindings. [Types](types.md) gives the type of each expression and when it fits where it is used.

## Literals

| Kind | Example |
|------|---------|
| Boolean | `true`, `false` |
| Integer | `42`, `-1` |
| Real | `3.14`, `1e-3` |
| String | `"hello"` |
| Nothing | `nothing` |
| Infinity | `*` |
| Array | `[a, b, c]`, `[]`, `[0, ...]` |
| Record (see [Records](datatypes.md#records)) | `Point { x = 0, y = 0 }` |

[Lexical Structure](lexical-structure.md#literals) gives the forms of the literals, and [Datatypes](datatypes.md) their types. The values of an array literal share a type, and `[a, b, c]` has the multiplicity `[3]`. A literal ending in `...`, such as `[0, ...]`, is the initial value of an array and takes its length from it ([Variables](../classes-and-behaviour/variables.md#multiplicity-on-the-type)).

## `self`

Inside a class body, `self` refers to the current class or feature instance. Outside a class, in a global feature or a record, it is rejected.

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

The binary operators are left-associative, and a range takes one `..` or `..<`. The guard and the branches of an if-then-else are whole expressions, so its `else` branch reaches as far to the right as it can: `if c then 1 else 2 + 3` is `if c then 1 else (2 + 3)`. A cast applies to a call or a chain of members and indices, so `-x as int` is `-(x as int)`. A call ends a chain of members and indices ([Calls and Arguments](#calls-and-arguments)).

As in Java, `&&` binds tighter than `^^`, which binds tighter than `||`: `a || b && c` parses as `a || (b && c)`. The validator warns on an operand that is another of the three written without parentheses and names the reading.

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

Their operands are Boolean, and so is their value. `^^` holds when exactly one of its operands holds.

```oxsts
class Switch {
  var up: bool := false
  var down: bool := false

  prop {
    return AG !(up && down) && ((up ^^ down) || !up)
  }
}
```

```oxsts issue=OXSTS.TYPE_MISMATCH
class Switch {
  var level: int := 0

  prop {
    return AG !level
  }
}
```

## Comparison Operators

| Operator | Description |
|----------|-------------|
| `<` | Less than |
| `<=` | Less than or equal |
| `>` | Greater than |
| `>=` | Greater than or equal |
| `==` | Equal |
| `!=` | Not equal |

`==` and `!=` take two values that compare ([Comparison and Joining](types.md#comparison-and-joining)), and `<`, `<=`, `>` and `>=` numeric operands, `int`, `real` or `any`; ordering other values, such as enum literals (`Color::Red < Color::Green`), is rejected.

Evaluated at compile time, as in an `inline if` guard, `==` between instance expressions (features, `self`, navigations, `nothing`) compares the sets of instances the two sides denote: they are equal when they hold the same instances, in any order. `nothing` is the empty set, so an empty side equals only another empty side, and `!=` is the negation of `==`.

## Arithmetic Operators

| Operator | Description |
|----------|-------------|
| `+` | Addition (binary) or unary plus |
| `-` | Subtraction (binary) or unary minus |
| `*` | Multiplication |
| `/` | Division |

Their operands are numeric, `int`, `real` or `any`, and their value is an `int` for two `int` operands and a `real` when either is `real`.

A division by zero has no value, and the operation that evaluates it blocks: an assignment, a local variable's initial value, an `assume`, the guard of an `if` or the range of a `for`. Where the division stands in its expression does not matter, since `&&`, `||` and an if-then-else whose guard reads the state guard none of their operands: `assume(divisor != 0 && total / divisor > 1)` and `share := if divisor != 0 then total / divisor else 0` block when `divisor` is 0. A divisor that is zero as a constant is rejected.

```oxsts issue=OXSTS.DIVISION_BY_ZERO
class Counter {
  var total: int := 0
  var share: int := 0

  redefine tran {
    share := total / 0
  }
}
```

```oxsts
class Counter {
  var value: int := 0
  var half: int := 0

  redefine tran {
    havoc(value)
    assume(0 <= value && value <= 10)
    half := value / 2 + value * 0 - 1
  }
}
```

```oxsts issue=OXSTS.TYPE_MISMATCH
class Counter {
  var on: bool := false
  var value: int := 0

  redefine tran {
    value := value + on
  }
}
```

## Ranges

| Syntax | Description |
|--------|-------------|
| `a..b` | Inclusive range from `a` to `b` |
| `a..<b` | Exclusive range from `a` to `b-1` |
| `a..*` | Every integer from `a` on |

```oxsts
class Counters {
  var total: int := 0

  redefine tran {
    inline for (index in 0..<3) {
      total := total + index
    }
  }
}
```

A range is an `int` range. `a..<a` is empty, and `a..<*` is unbounded like `a..*`. Ranges are the ranges of `for` and `inline for` and, in brackets, [multiplicities](../classes-and-behaviour/features.md#multiplicities).

## Member Access (`.`)

The `.` operator names a member of an instance. When the instance is held by a variable, the member of the instance the variable holds is read (see [Feature-Typed Variables](../classes-and-behaviour/variables.md#feature-typed-variables)). A read or a property call through a variable that holds `nothing` has no value, and the operation of a transition that evaluates it blocks, wherever the read stands in the expression: in a branch of an if-then-else or an operand of `&&` as anywhere else.

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

`?.` navigates through an optional (`[0..1]`) reference. An `inline` call whose receiver is absent is dropped, and the rest of the transition runs, also when the receiver is a variable that holds `nothing` as the model runs; an `inline` call takes `?.` exactly when its receiver is optional ([Inline Call](../classes-and-behaviour/inline-operations.md#inline-call)). Reading a member through an absent receiver fails compilation, and a read or a property call through a variable takes `.` only.

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

`a[i]` is the slot `i` of the array `a`, counted from 0, and has the array's domain with the multiplicity `[1]`. The index is numeric, and indexing a value whose multiplicity's upper bound is at most one, such as an `int` variable, is rejected. The index may read the state, and then the slot is chosen when the model runs. A slot is read and written like a variable, and a slot of an array whose lower bound is 0, such as `Item[0..2]`, takes `nothing` when it is written.

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

```oxsts issue=OXSTS.TYPE_MISMATCH
class Buffer {
  var count: int := 0

  prop first(): int {
    return count[0]
  }
}
```

## Calls and Arguments

`f(arguments)` calls a [property](../classes-and-behaviour/properties.md) or a [transition](../classes-and-behaviour/transitions.md). A property call is an expression whose value is the property's; a transition call is what an [inline call](../classes-and-behaviour/inline-operations.md#inline-call) runs. The callee is named like a member, by itself, through `self` or through a navigation, and calling anything else, such as a variable or a record, is rejected. A call ends a chain of members and indices, and only a cast may follow it: `a.b.f()` calls `f` on `a.b`, while `a.f().b`, `f()()` and `a.f()[0]` are syntax errors, and a member of what a call gives is read through parentheses, `(a.f()).b`.

These rules bind the arguments of a property call and of an inline call; the first, the second and the last bind the arguments of an [annotation](../library-and-output/annotations.md#with-arguments) too:

- An argument is positional or named, `name = value`. A positional argument binds the parameter at its position, a named one the parameter of its name.
- Every named argument follows all positional ones, and a parameter is bound at most once, by position or by name. {/* OXSTS.POSITIONAL_ARGUMENT_AFTER_NAMED */}
- A parameter whose multiplicity has the lower bound 0, such as `[0..1]`, is optional and binds `nothing` when it is left out. Every other parameter must be bound, and a call has no more arguments than parameters; a required parameter is declared before every optional one, or the declaration is rejected. {/* OXSTS.INVALID_CALL_ARGUMENTS_COUNT */}
- Each argument fits its parameter's type ([Assignability](types.md#assignability)).

A property call's value is the value of the property's body with every parameter replaced by its argument, read against the instance the call names; an inline call passes its arguments by value ([Inline Call](../classes-and-behaviour/inline-operations.md#inline-call)).

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

```oxsts
class Counter {
  var value: int := 0
}

class Pair {
  contains a: Counter

  prop first(): Counter {
    return a
  }

  prop firstValue(): int {
    return (first()).value
  }
}
```

```oxsts issue=syntax
class Counter {
  var value: int := 0
}

class Pair {
  contains a: Counter

  prop first(): Counter {
    return a
  }

  prop firstValue(): int {
    return first().value
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

`expression as Type` gives an expression a narrower type, as a promise that its value fits that type. A cast is accepted when the named type fits the expression's own ([Assignability](types.md#assignability)): to a subclass, to a subsetting feature, from `any` to a datatype, to a smaller multiplicity such as from `[0..*]` to `[1]`, or to the same type. A cast that widens, such as from a class to its superclass or to `any`, or one between unrelated types, is rejected. A cast changes nothing when the model runs: the compiler checks the same rule again and then drops it, so nothing checks the promise, and the value is used as it is.

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

`if guard then a else b` is `a` when the guard holds and `b` otherwise. The guard is Boolean, the `else` branch is required, and the two branches join ([Comparison and Joining](types.md#comparison-and-joining)); either is rejected otherwise. Its type is the joined type of the branches, so `if dark then lamp else nothing` holds at most one instance. Unlike the [`if` operation](../classes-and-behaviour/operations.md#if), it is an expression and changes no state. A guard evaluated at compile time, one that reads no state, decides the expression when the case is compiled, as the guard of an `inline if` does, and the branch not taken is not compiled.

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

Temporal operators denote logical formulae over the possible executions of the system. Their body is Boolean.

| Operator | Meaning |
|----------|---------|
| `AG expr` | Always globally. `expr` holds in **every** reachable state. Use for safety properties. |
| `EF expr` | Exists in the future. `expr` holds in **some** reachable state. Use for reachability checks. |

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

A temporal operator is only allowed as the whole expression a property returns: one in a transition (`flag := AG true`), below another operator or nested in another temporal operator (`AG (EF on)`) is rejected. {/* OXSTS.MISPLACED_TEMPORAL_OPERATOR */}

<Planned table={constructs} construct="CTL* properties">

Support of CTL* properties is planned. The parser rejects every temporal operator but `AG` and `EF` today.

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
