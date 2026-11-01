---
title: Datatypes
sidebar_position: 2
---

# Datatypes

This page describes the **data values** Semantifyr can represent: primitives, enumerations and records.

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

A `record` declares a value type that groups named fields. Records have no identity: assignment copies every field, and two records are equal when all their fields are equal. Records have no inheritance and no behavior; they only carry data. A field is a primitive, an enumeration or another record, and its initializer is the default value of the field.

```oxsts
record Point {
  var x: int := 0
  var y: int := 0
}

record Segment {
  var from: Point
  var to: Point
}
```

A variable of record type holds one record. Fields are read and written with `.`, a whole record is assigned with `:=` and compared with `==` and `!=`, and `havoc` picks an arbitrary value for every field. A record variable without an initializer starts from the field defaults; a field without a default is havoced, like an uninitialized variable.

```oxsts
class Plotter {
  var pen: Point
  var mark: Point

  redefine tran {
    choice {
      pen.x := pen.x + 1
    } or {
      mark := pen
    }
  }

  prop {
    return AG (mark == pen || mark.x < pen.x)
  }
}
```

A record literal names the record and gives fields by name. An omitted field takes its default, so a literal must give every field that has none (`OXSTS.INCOMPLETE_RECORD_LITERAL` otherwise) and names at least one field: `Point {}` is not a literal. The values may read the state, literals nest, and the whole value is read before the target is written, so a literal can swap fields. A literal can be assigned, initialize a variable, be compared with `==` and `!=`, and be passed as an argument; it cannot be written or havoced.

```oxsts
var origin: Point := Point { x = 0, y = 0 }

redefine tran {
  pen := Point { x = pen.y, y = pen.x }
  stroke := Segment {
    from = origin,
    to = Point { x = pen.x + 1 }
  }
}
```

The compiler flattens a record variable into one variable per field, nested records recursively, and the witness shows the state field by field, such as `root.pen.x`. The validator rejects arrays of records, array fields, fields of class or feature type, record-typed features, recursive records (`OXSTS.RECURSIVE_RECORD`), ordering comparisons such as `<` on records, and a record literal in a position evaluated at compile time, such as an `inline if` guard.
