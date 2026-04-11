---
title: Datatypes
sidebar_position: 2
---

# Datatypes

This page describes the **data values** Semantifyr can represent: primitives, value-typed records, and enumerations.

## Primitive Datatypes

The [standard library](standard-library.md) declares the following primitive datatypes:

| Type | Description |
|------|-------------|
| `int` | Integer numbers |
| `bool` | Boolean values: `true` or `false` |
| `real` | Real numbers |
| `string` | Sequences of characters |

Backend support varies. See [Standard Library / Backend support](standard-library.md#backend-support) for which primitives the Theta backend handles.

## Enumerations

An `enum` declares a type with a fixed set of named literals.

```oxsts
enum Color {
  Red,
  Green,
  Blue
}
```

Enum literals are referenced with `::`:

```oxsts
if (current == Color::Red) {
  current := Color::Green
}
```

## Records

Records are **value-typed** objects. They have no identity: two records with equal field values are interchangeable. Records are analogous to Java Valhalla value classes or C structs. Records have no inheritance and no behavior; they only carry data.

```oxsts
record Point {
  var x: int
  var y: int
}
```

A record literal lists the fields by name inside curly braces:

```oxsts
var origin: Point := Point {
  x = 0,
  y = 0
}
```

For an identity-bearing alternative, see [Classes](classes.md).
