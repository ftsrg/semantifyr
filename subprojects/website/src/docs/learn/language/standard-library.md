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

All four backends (Theta, nuXmv, Spin and Uppaal) support variables of type `int`, `bool` and enumerations. No backend supports `real` or `string` variables yet. The `any` type is a structural feature of the language and works regardless of backend. A backend reports a model it cannot translate as not supported before it runs, naming each construct and its source line: besides these types, runtime [`for`](operations.md#for) runs only on Theta and only over a range, and Spin cannot evaluate an if-then-else expression of a non-boolean type inside a property.

## The `Anything` Class

Implicit supertype of all classes. Provides empty base transitions:

```oxsts
class Anything {
  tran { }
  init { }
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

Categorizes verification cases. For example, `@Tag(category = "slow")` marks long-running cases. The repository's conformance suites read `@Tag("expect-pass")` and `@Tag("expect-fail")` on a case as its expected verdict, and `@Tag("engine-limit:spin,uppaal")` as the engines that cannot decide it: an Inconclusive or Not supported verdict from those engines passes, a decisive one must still match.

### `@Control`

```oxsts
annotation Control
```

Declares the variable a control variable of the Theta model (`ctrl var`), which Theta's `cegar-expl` and `cegar-expl-pred-combined` configurations track precisely from the first abstraction. The other engines ignore it, and counterexamples carry every variable either way, so mark only variables that steer the model, never ones that only record what happened.

### `@Shared`

```oxsts
annotation Shared
```

Instances contained in a `@Shared` containment feature are shared across all instances of the containing class, rather than duplicated per instance.

### `@Trace`

```oxsts
annotation Trace
```

Calls to `@Trace`-annotated transitions are recorded in the verification witness, including parameter bindings at each call site. Only the calls that fired are listed, each with the receiver's feature path from the root instance (`root.kitchen.lamp`). The witness files `trace.json` and `witness.json` hold `initialStep` (the state before `init`), `initializedStep` (the state after `init`, with the calls traced during it) and `steps`, one per step of the main transition. The SysML v2 library traces a minimal set: transition firing, item sending, and the time advance at the root. Each traced call site the compiler keeps costs one boolean state variable, which is not a `@Control` variable.

### `@Clock`

```oxsts
annotation Clock
```

Marks a variable as a clock. Uppaal models it as a clock; the other backends treat it as an ordinary variable. Uppaal accepts `@Clock` only on data-typed variables, resets a clock only to `0` and cannot havoc it; it reports anything else as not supported.

### `@NonOptimizable`

```oxsts
annotation NonOptimizable
```

Excludes a variable from optimizer passes that could remove or fold it.
