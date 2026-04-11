---
title: Expressions
sidebar_position: 4
---

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
| Record | `Point { x = 0, y = 0 }` |

See [Datatypes](datatypes.md) for the corresponding types.

## `self`

Inside a class body, `self` refers to the current class or feature instance.

```oxsts
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
| 1 | Boolean | `&&`, `\|\|`, `^^` |
| 2 | Comparison | `<`, `<=`, `>`, `>=`, `==`, `!=` |
| 3 | Range | `..`, `..<` |
| 4 | Additive | `+`, `-` |
| 5 | Multiplicative | `*`, `/` |
| 6 | Unary | `!`, unary `+`, unary `-` |
| 7 | Postfix / member | `.`, `?.`, `[ ]`, `( )` |

The boolean operators `&&`, `||`, and `^^` share one precedence level and are left-associative: `a && b || c` parses as `(a && b) || c`.

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

The `.` operator names a member of an instance.

```oxsts
counter.value
```

### Optional Member Access (`?.`)

`?.` navigates through an optional (`[0..1]`) reference. If the receiver is `nothing`, the entire expression evaluates to `nothing`.

```oxsts
parent?.value
```

## Indexing (`[]`)

Accesses an array element by index.

```oxsts
items[0]
```

## Property Invocation (`()`)

A [property](properties.md) is invoked using the `()` invocation expression.

```oxsts
prop atLimit(): bool {
  return value == maximum()
}

prop pairAtLimit(): bool {
  return a.atLimit() && b.atLimit()
}
```

## Temporal Operators

Temporal operators uses to denote logical formulae over the possible executions of the system.

| Operator | Meaning |
|----------|---------|
| `AG expr` | Always globally. `expr` holds in **every** reachable state. Use for safety properties. |
| `EF expr` | Exists finally. `expr` holds in **some** reachable state. Use for reachability checks. |

```oxsts
prop {
  return AG counter.value < 100
}
```

:::note
Additional temporal operators will be supported in the future.
:::
