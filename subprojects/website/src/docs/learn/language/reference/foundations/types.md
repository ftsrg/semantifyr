---
title: Types
slug: /learn/language/reference/types
sidebar_position: 3
description: The type system of the Semantifyr Language, which values can be assigned where, casts, and comparison.
---

# Types

Every expression has a type. A type has two parts: a domain (the kind of value), and a multiplicity (how many values it holds).

The domain is a [datatype](datatypes.md) or a [class](../classes-and-behaviour/classes.md). The multiplicity is written in brackets after the domain, as in `Item[0..1]`, and is `[1]` by default. `[0..1]` holds one value or `nothing` (no value), and `[3]` holds three values.

## Assignability

A variable accepts a value of its own type. A variable of a class also accepts instances of its subclasses, and a variable of type `any` accepts any value:

```oxsts
// hide-start
class Example {
  redefine tran {
// hide-end
    var count: int := 10
    var value: any := count
// hide-start
  }
}
// hide-end
```

Numbers are not converted, so an `int` cannot be assigned to a `real` variable:

```oxsts issue=OXSTS.TYPE_MISMATCH
// hide-start
class Example {
  redefine tran {
// hide-end
    var count: int := 10
    var ratio: real := count // an error
// hide-start
  }
}
// hide-end
```

An optional variable, such as `Item[0..1]`, accepts a value or `nothing`. A variable of multiplicity `[1]` does not accept an optional value, so cast it to a single value first. An array accepts only an array of the same length.

The same rules apply to arguments and return values.

## Casts

Use `as` to cast a value to a more specific type, such as from `any` to `int`, from a class to one of its subclasses, or from an optional value to a single one:

```oxsts
// hide-start
class Example {
  redefine tran {
// hide-end
    var value: any := 10
    var count: int := value as int
// hide-start
  }
}
// hide-end
```

Casting to a more general type, or to an unrelated type, is an error. The compiler checks that the value has that type.

## Comparison

`==` and `!=` compare two values if one could be assigned to a variable of the other's type. `<`, `<=`, `>`, and `>=` compare numbers only.

Any value can be compared with `nothing`, except a record. Records and arrays can only be compared with `==` and `!=`, and two arrays only if they have the same length.

The two branches of an [if-then-else](../classes-and-behaviour/operations.md#if-then-else) must be comparable. The result has the more general of the two types, and it is optional if one branch is `nothing`:

```oxsts
// hide-start
abstract class Shape

class Square : Shape

class Example {
  redefine tran {
// hide-end
    var filled: bool := true
    var square: Square[0..1] := nothing
    var shape: Shape[0..1] := if filled then square else nothing // a Square, or nothing
// hide-start
  }
}
// hide-end
```
