---
title: Annotations
sidebar_position: 13
---

# Annotations

Annotations attach metadata to declarations. They do not change behavior directly but are read by the Semantifyr toolchain (for example, to identify verification cases or to record traces).

## Using an Annotation

The `@` prefix attaches an annotation to the immediately following declaration.

```oxsts
// hide-start
class Counter {
  var value: int := 0
}

// hide-end
@VerificationCase
class StaysBelow100 {
  contains counter: Counter

  prop {
    return AG counter.value < 100
  }
}
```

```oxsts
// hide-start
class Counter {
// hide-end
  @Control
  var value: int := 0
// hide-start
}
// hide-end
```

## With Arguments

Annotation arguments bind as call arguments do ([Calls and Arguments](expressions.md#calls-and-arguments)). Arguments must be constants: annotations are read without an instance, so the validator rejects a reference to a feature or variable (`OXSTS.EXPRESSION_MODALITY_TOO_HIGH`).

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

A custom annotation may then be applied like any built-in.

```oxsts continue
// hide-start
class Counter {
  var value: int := 0

// hide-end
  @Description(text = "Increments the counter by one")
  tran increment() {
    value := value + 1
  }
// hide-start
}
// hide-end
```

## Standard Annotations

The [Standard Library](standard-library.md#annotations) declares the standard annotations and describes each. A standard annotation on a declaration it does not apply to is rejected (`OXSTS.INCORRECT_ANNOTATION_TARGET`): `@VerificationCase` and `@Tag` apply to classes, `@Shared` to containment features, `@Trace` to transitions, and `@Control`, `@Clock` and `@NonOptimizable` to variables. The compiler reads no annotation it does not know, and a user-declared annotation is not checked against its target.

```oxsts issue=OXSTS.INCORRECT_ANNOTATION_TARGET
@Trace
class Lamp {
}
```
