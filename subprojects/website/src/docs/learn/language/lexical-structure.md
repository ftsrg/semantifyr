---
title: Lexical Structure
sidebar_position: 0.5
---

# Lexical Structure

A file is a sequence of names, keywords, literals and symbols, separated by whitespace and comments, which have no meaning of their own. Line breaks are whitespace, and a semicolon after a declaration or an operation is optional.

## Names

A name starts with a letter or `_` and goes on with letters, digits and `_`, ASCII only: `counter`, `Light2`, `_next`. Case matters. A qualified name joins names with `::`, such as `example::lights::Lamp`, or `Color::Red` for an enum literal.

A name between single quotes, `'Traffic light'`, may hold any character but a single quote and a line break, spaces and keywords among them; the quotes are not part of the name, so `'Lamp'` and `Lamp` name the same thing.

```oxsts
class 'Traffic light' {
  var 'is green': bool := false
}

class Crossing {
  contains 'main light': 'Traffic light'

  prop {
    return EF 'main light'.'is green'
  }
}
```

Every keyword is reserved and cannot be a name without quotes, except `init`, `havoc` and `prop`, which may also be names. A class inherits the transition `init` of [`Anything`](classes.md#the-anything-base-class), so a member named `init` hides it (`OXSTS.ELEMENT_SHADOWED`).

```oxsts
class Recorder {
  var havoc: int := 0

  tran store(prop: int, init: bool) {
    assume(init)
    self.havoc := prop
  }
}
```

## Comments

`//` starts a comment that ends with the line, and `/*` a comment that ends at the next `*/`; comments do not nest. A `/* */` comment right before a declaration is its documentation, which the editors show on hover.

```oxsts
/*
 * A lamp that is on or off.
 */
class Lamp {
  // off at the start
  var on: bool := false
}
```

## Literals

- An integer is a sequence of decimal digits, `0` to `2147483647`; a larger one is a syntax error. A minus is the [unary operator](expressions.md#arithmetic-operators), so `-1` is the negation of `1`, and the least integer of 32 bits is written `-2147483647 - 1`.
- A real number has digits on both sides of its point, an exponent or both: `3.14`, `1e-3`, `2.5E+6`. `.5` and `5.` are not literals.
- A string is a sequence of characters between double quotes, `"hello"`, with no double quote and no line break inside and no escape sequence.
- `true` and `false` are the Boolean literals, `nothing` the absence of a value and `*` an unbounded upper bound; array and record literals are [expressions](expressions.md#literals).

```oxsts
annotation Measured(unit: string, scale: real)

@Measured(unit = "metres", scale = 2.5E+6)
class Rail {
  var length: int := 2147483647
  var offset: int := -2147483647 - 1
}
```
