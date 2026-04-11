---
title: Standard Library
sidebar_position: 14
---

# Standard Library

The `semantifyr` package is implicitly available in every Semantifyr file. It defines the built-in datatypes, the `Anything` base class, and the standard annotations.

## Extern Datatypes

| Type | Description |
|------|-------------|
| `any` | Universal supertype of all types |
| `int` | Integer numbers |
| `real` | Real numbers |
| `bool` | Boolean values |
| `string` | Text values |

## Backend Support

Not every model checker backend supports every primitive datatype. The Theta backend currently supports:

| Primitive | Theta backend |
|-----------|---------------|
| `int` | Supported |
| `bool` | Supported |
| `real` | Limited / not supported |
| `string` | Limited / not supported |

Use of `real` and `string` requires a backend with the corresponding theory. The `any` type is a structural feature of the language and works regardless of backend.

## The `Anything` Class

Implicit supertype of all classes. Provides empty base transitions:

```oxsts
class Anything {
  tran { }
  init { }
  havoc { }
}
```

Every class you write inherits these. Override them with `redefine`.

## Annotations

### `@VerificationCase`

```oxsts
annotation VerificationCase(summary: string[0..1])
```

Marks a class as a verification target. The optional `summary` parameter describes what the case checks.

### `@Tag`

```oxsts
annotation Tag(category: string)
```

Categorizes verification cases. For example, `@Tag(category = "slow")` marks long-running cases.

### `@Control`

```oxsts
annotation Control
```

Signals that a variable is important to track during verification. May affect how the model checker reports counterexamples.

### `@Shared`

```oxsts
annotation Shared
```

Instances contained in a `@Shared` containment feature are shared across all instances of the containing class, rather than duplicated per instance.

### `@Trace`

```oxsts
annotation Trace
```

Calls to `@Trace`-annotated transitions are recorded in the verification witness, including parameter bindings at each call site.
