---
title: Classes
sidebar_position: 6
---

# Classes

A `class` describes a part of the modeled system. Classes have state ([variables](variables.md)), structure ([features](features.md)), and behavior ([transitions](transitions.md), [properties](properties.md)).

## The `Anything` Base Class

Every class declared in Semantifyr implicitly extends `Anything` from the [standard library](standard-library.md). `Anything` provides empty `init`, `tran`, and `havoc` transitions:

```oxsts
class Anything {
  tran { }
  init { }
  havoc { }
}
```

Because these transitions are inherited, declaring your own [`init`](transitions.md), [implicit `tran`](transitions.md#the-implicit-main-init-and-havoc) (named `main`), or [`havoc`](transitions.md) requires `redefine`. See [Redefining members](#redefining-members).

## Class Declaration

A class declaration consists of the `class` keyword, a name, an optional list of superclasses, and a body in `{ }`. Members may appear in any order.

```oxsts
class Counter {
  var value: int := 0

  tran increment() {
    value := value + 1
  }

  prop isPositive(): bool {
    return value > 0
  }
}
```

A class with no members may be written with an empty body.

```oxsts
class Sentinel {
}
```

Or without any.

```oxsts
class Sentinel
```

:::tip
Generally Semantifyr is lenient with the use or absence of semicolons `;`.
:::

## Abstract Classes

`abstract class` cannot be instantiated directly. An abstract class may declare abstract members (without bodies) that concrete subclasses must redefine.

```oxsts
abstract class Shape {
  abstract prop area(): real
}
```

## Inheritance

A class extends one or more superclasses listed after `:`, separated by commas.

```oxsts
class Square : Shape {
  var side: int := 1

  redefine prop area(): real {
    return side * side
  }
}
```

{/* Not yet
Multiple inheritance is also supported.

```oxsts
class Logged : Counter, Loggable {
}
```
*/}

## Redefining Members

`redefine` overrides an inherited member of the **same name**. The redefining member must match the signature (parameter types and return type for [transitions](transitions.md) and [properties](properties.md)). Redefining is the primary mechanism by which semantic libraries specialize behavior.

```oxsts
redefine tran fire() {
  inline target.activate()
}
```

```oxsts
redefine prop evaluate(): int {
  return value
}
```

```oxsts
redefine contains entryAction: SendAction
```

## `self`

Inside a class body, `self` refers to the current instance of that class or [feature](features.md). It is typically used to pass the current instance as an argument.

```oxsts
class Counter {
  contains parent: Group

  redefine init {
    inline parent.register(self)
  }
}
```
