---
title: Annotations
slug: /learn/language/reference/annotations
sidebar_position: 2
description: The annotations of the Semantifyr Language, how to apply them, pass their arguments, and declare your own.
---

# Annotations

An annotation attaches metadata to a declaration. The toolchain reads the standard annotations, such as `@VerificationCase`, and ignores the ones you declare yourself.

## Applying an Annotation

Write `@` and the annotation's name before a declaration:

```oxsts
@VerificationCase
class StaysBelow100 {
  @Control
  var value: int := 0

  redefine prop {
    return AG value < 100
  }
}
```

## Arguments

Pass arguments in parentheses, by position or by name, as in a [property call](../classes-and-behaviour/properties.md#calling-a-property). This case has a summary and a tag:

```oxsts
@VerificationCase("Never negative")
@Tag(category = "slow")
class NeverNegative {
  var value: int := 0

  redefine prop {
    return AG value >= 0
  }
}
```

Unlike in a call, leaving out a required parameter is not an error. Arguments must be constants, because an annotation has no instance: an argument that reads a variable or a feature is an error.

## Standard Annotations

The [standard library](standard-library.md#annotations) declares the standard annotations and describes what each one does. Each applies to one kind of declaration, and using it on another is an error:

```oxsts issue=OXSTS.INCORRECT_ANNOTATION_TARGET
@Trace // an error
class Lamp {
}
```

An annotation belongs to the declaration it is written on. A subclass of a verification case is a case only if it is marked too, and a redefinition does not keep the annotations of the member it redefines. `@Trace` is the exception: a redefinition of a traced transition is traced as well.

## Declaring an Annotation

Declare an annotation with `annotation`, a name, and its parameters, if it has any:

```oxsts
annotation Important

annotation Description(text: string)
```

Parameters are declared as for a [property](../classes-and-behaviour/properties.md#parameters). This transition carries a description:

```oxsts continue
class Counter {
  var value: int := 0

  @Description("Increments the value by one")
  tran increment() {
    value := value + 1
  }
}
```

An annotation you declare can go on any kind of declaration.
