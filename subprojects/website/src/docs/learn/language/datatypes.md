---
title: Datatypes
sidebar_position: 2
---

# Datatypes

This page describes the **data values** Semantifyr can represent: primitives and enumerations.

## Primitive Datatypes

The [standard library](standard-library.md) declares the following primitive datatypes:

| Type | Description |
|------|-------------|
| `int` | Integer numbers |
| `bool` | Boolean values: `true` or `false` |
| `real` | Real numbers |
| `string` | Sequences of characters |

Backend support varies. See [Standard Library / Backend support](standard-library.md#backend-support) for which primitives the backends handle.

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

Records are not supported yet. The grammar accepts `record` declarations, but the validator rejects them. Use [Classes](classes.md) to group data.
