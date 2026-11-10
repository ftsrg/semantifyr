---
title: Annotations
sidebar_position: 13
---

# Annotations

Annotations attach metadata to declarations. They do not change behavior directly but are read by the Semantifyr toolchain (for example, to identify verification cases or to record traces).

## Using an Annotation

The `@` prefix attaches an annotation to the immediately following declaration.

```oxsts fragment
@VerificationCase
class Reaches10 {
  contains counter: Counter

  prop {
    return AG counter.value < 100
  }
}
```

```oxsts fragment
@Control
var value: int := 0
```

## With Arguments

Annotation arguments bind like call arguments: a named argument (`name = value`) binds the parameter of that name, a positional argument the parameter at its position, and every named argument follows all positional arguments. Arguments must be constants: annotations are read without an instance, so the validator rejects a reference to a feature or variable (`OXSTS.EXPRESSION_MODALITY_TOO_HIGH`).

```oxsts
@VerificationCase(summary = "Reaches the limit")
class Reaches10 {
  var value: int := 0

  prop {
    return EF value == 10
  }
}
```

```oxsts
@Tag(category = "slow")
class LargeTest {
}
```

## Stacking Annotations

Multiple annotations may be applied to a single declaration. Each starts on its own `@`.

```oxsts
@VerificationCase
@Tag(category = "slow")
class LargeTest {
  var value: int := 0

  prop {
    return AG value >= 0
  }
}
```

## Declaring Custom Annotations

Without parameters:

```oxsts
annotation Important
```

With parameters:

```oxsts
annotation Description(text: string)
```

A custom annotation may then be applied like any built-in. A variable keeps its annotations in the verified model, so an annotation on a variable takes no string argument (`OXSTS.UNSUPPORTED_FEATURE`).

```oxsts fragment
@Description(text = "Increments the counter by one")
tran increment() {
  value := value + 1
}
```

## Standard Annotations

See the [Standard Library](standard-library.md) for full details. A standard annotation on a declaration it does not apply to is rejected (`OXSTS.INCORRECT_ANNOTATION_TARGET`): `@VerificationCase` and `@Tag` apply to classes, `@Shared` to containment features, `@Trace` to transitions, and `@Control`, `@Clock` and `@NonOptimizable` to variables.

| Annotation | Purpose |
|------------|---------|
| `@VerificationCase` | Marks a class as a verification target |
| `@Tag` | Categorizes verification cases |
| `@Control` | Marks a variable as important to track |
| `@Shared` | Marks containment feature instances as shared across instances |
| `@Trace` | Marks transitions for counterexample tracing |
| `@Clock` | Marks a variable as a clock |
| `@NonOptimizable` | Excludes a variable from optimization |
