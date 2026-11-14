---
title: Lexical Structure
sidebar_position: 0.5
description: The lexical structure of OXSTS, its names, keywords, literals, comments and whitespace.
---

# Lexical Structure

A file is a sequence of names, keywords, literals and symbols, separated by whitespace and comments, which have no meaning of their own. Line breaks are whitespace. A semicolon may end what does not end in a block: a `package` or `import` line, a declaration without a body, a variable, a feature without a nested block, the expression a property returns, an assignment, a `havoc`, an `assume` and an inline call. After a closing brace it is a syntax error.

## Names

A name starts with a letter or `_` and goes on with letters, digits and `_`, ASCII only: `counter`, `Light2`, `_next`. Case matters. A qualified name joins names with `::`, such as `example::lights::Lamp`, or `Color::Red` for an enum literal.

A name between single quotes, `'Traffic light'`, may hold spaces and keywords, and any character but a single quote and a line break; a backslash in it starts an escape sequence, as in a [string](#literals), with `\"` in place of `\'`. The quotes are not part of the name, so `'Lamp'` and `Lamp` name the same thing.

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
    assume(init);
    self.havoc := prop;
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
- A string is a sequence of characters between double quotes, `"hello"`, with no double quote and no line break inside. A backslash starts an escape sequence: `\b`, `\t`, `\n`, `\f`, `\r`, `\'`, `\\` and a Unicode escape such as `\u0041`; any other, such as `\q`, is a syntax error.
- `true` and `false` are the Boolean literals, `nothing` the absence of a value and `*` an unbounded upper bound; array and record literals are [expressions](expressions.md#literals).

```oxsts
annotation Measured(unit: string, scale: real, label: string[0..1])

@Measured(unit = "metres", scale = 2.5E+6, label = "length\tin metres")
class Rail {
  var length: int := 2147483647
  var offset: int := -2147483647 - 1
}
```

```oxsts issue=org.eclipse.xtext.diagnostics.Diagnostic.Syntax.Range
annotation Note(text: string)

@Note(text = "a \q here")
class Rail {
}
```

```oxsts issue=syntax
class Rail {
  var length: int := 0
};
```
