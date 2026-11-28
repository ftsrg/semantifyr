---
title: Datatypes
slug: /learn/language/reference/datatypes
sidebar_position: 2
description: The datatypes of the Semantifyr Language, numbers, Booleans, strings, enumerations, and records, with ranges and operator precedence.
---

import Planned from '@site/src/components/Planned';

# Datatypes

This page describes the datatypes of the Semantifyr Language: numbers, Booleans, strings, enumerations, and records.

## Numbers

There are two number types: `int` for integers and `real` for real numbers. Numbers have no fixed size, so an `int` can be any integer and a `real` any real number.

Write integers in decimal, and reals with a decimal point or an exponent:

```oxsts
// hide-start
class Example {
// hide-end
  var count: int := 42
  var ratio: real := 3.14
  var distance: real := 2.5E+6
// hide-start
}
// hide-end
```

Numbers support the arithmetic operators `+`, `-`, `*`, and `/`, and the unary `+` and `-`. Arithmetic on two `int`s gives an `int`, and arithmetic with a `real` gives a `real`:

```oxsts
// hide-start
class Example {
  redefine tran {
// hide-end
    var count: int := 7
    var half: int := count / 2 // 3
    var ratio: real := count / 2.0 // 3.5
// hide-start
  }
}
// hide-end
```

Compare numbers with `==`, `!=`, `<`, `<=`, `>`, and `>=`. An `int` cannot be compared with a `real`.

Dividing by a constant zero is an error. Any other division by zero has no value, and the operation that evaluates it [blocks](../classes-and-behaviour/operations.md#blocking).

## Ranges

A range is a sequence of consecutive integers. `a..b` includes both `a` and `b`, `a..<b` stops before `b`, and `a..*` has no end. Ranges are used in `for` loops and in multiplicities:

```oxsts
// hide-start
class Example {
  var total: int := 0

  redefine tran {
// hide-end
    inline for (index in 0..<3) { // index is 0, 1, and 2
      total := total + index
    }
// hide-start
  }
}
// hide-end
```

## Booleans

The `bool` type has two values, `true` and `false`. The Boolean operators are `!` (not), `&&` (and), `||` (or), and `^^` (exclusive or):

```oxsts
// hide-start
class Example {
  redefine tran {
// hide-end
    var on: bool := true
    var off: bool := !on // false
    var both: bool := on && off // false
    var either: bool := on || off // true
    var different: bool := on ^^ off // true
// hide-start
  }
}
// hide-end
```

`&&` and `||` always evaluate both operands, so one side cannot guard the other. `divisor != 0 && total / divisor > 1` still blocks when `divisor` is 0.

## Strings

The `string` type holds text. Write strings in double quotes, as in `"hello"`. A string cannot contain a double quote or a line break, but it can contain escapes such as `\n` and `\u0041`. The backends do not support strings, so strings appear mostly as annotation arguments, as in `@Tag("slow")`.

## Enumerations

An enumeration is a type with a fixed set of named values, its literals. Declare one with `enum`:

```oxsts
enum Color {
  Red,
  Green,
  Blue,
}
```

Refer to a literal with `::`, as in `Color::Red`. Literals have no order, so they can be compared only with `==` and `!=`:

```oxsts continue
// hide-start
class Light {
  redefine tran {
// hide-end
    var current: Color := Color::Red
    var stopped: bool := current == Color::Red // true
// hide-start
  }
}
// hide-end
```

## Records

A record groups named fields into one value. Declare one with `record`, and give each field a type and a default value:

```oxsts
record Point {
  var x: int := 0
  var y: int := 0
}
```

A field's type is a primitive datatype, an enumeration, or another record. A record cannot contain itself.

To create a record value, write the record's name and the fields you set, as in `Point { x = 1 }`. The other fields keep their default values. Set at least one field, so a point with only default values is `Point { x = 0 }`.

Unlike an instance of a class, a record is a value: assigning a record copies all its fields, and two records are equal when all their fields are equal. Read and write fields with `.`:

```oxsts continue
// hide-start
class Plotter {
  redefine tran {
// hide-end
    var pen: Point := Point { x = 1 }
    var mark: Point := pen // a copy of pen
    pen.x := 2 // mark.x is still 1
    pen := Point { x = pen.y, y = pen.x } // swaps x and y
// hide-start
  }
}
// hide-end
```

<Planned>

Some uses of records are not supported yet: features and optional variables of record type, array fields, fields of class or feature type, records in expressions the compiler evaluates, such as an `inline if` condition, and records as arguments of a call to a [`@Trace`](../library-and-output/standard-library.md#trace) transition. Arrays of records work only as variables of a class.

</Planned>

## Precedence

Operators bind in this order, from loosest to tightest: `AG` and `EF`, `||`, `^^`, `&&`, comparisons, ranges, `+` and `-`, `*` and `/`, unary operators and if-then-else, `as`, calls, and finally `.`, `?.`, and indexing. So `a + b * c` means `a + (b * c)`, and `AG a || b` means `AG (a || b)`.

Operators of the same precedence group to the left, so `a - b - c` means `(a - b) - c`. The `else` branch of an if-then-else reaches as far to the right as it can, so `if c then a else b + 1` means `if c then a else (b + 1)`.

Mixing `&&`, `^^`, and `||` without parentheses gives a warning, because the order is easy to misread:

```oxsts issue=OXSTS.MIXED_BOOLEAN_OPERATORS
// hide-start
class Example {
  var up: bool := false
  var down: bool := false
  var locked: bool := false

  prop mixed(): bool {
// hide-end
    return up || down && !locked // a warning
// hide-start
  }
}
// hide-end
```
