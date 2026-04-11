---
title: Annotations
sidebar_position: 13
---

# Annotations

Annotations attach metadata to declarations. They do not change behavior directly but are read by the Semantifyr toolchain (for example, to identify verification cases or to record traces).

## Using an Annotation

The `@` prefix attaches an annotation to the immediately following declaration.

```oxsts
@VerificationCase
class Reaches10 {
  contains counter: Counter

  prop {
    return AG counter.value < 100
  }
}
```

```oxsts
@Control
var value: int := 0
```

## With Arguments

Annotation arguments use named-argument syntax (`name = value`).

```oxsts
@VerificationCase(summary = "Reaches the limit")
class Reaches10 {
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

A custom annotation may then be applied like any built-in:

```oxsts
@Description(text = "Increments the counter by one")
tran increment() {
  value := value + 1
}
```

## Standard Annotations

See the [Standard Library](standard-library.md) for full details.

| Annotation | Purpose |
|------------|---------|
| `@VerificationCase` | Marks a class as a verification target |
| `@Tag` | Categorizes verification cases |
| `@Control` | Marks a variable as important to track |
| `@Shared` | Marks containment feature instances as shared across instances |
| `@Trace` | Marks transitions for counterexample tracing |
