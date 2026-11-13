---
title: Classes
sidebar_position: 6
---

import LibraryDeclaration from '@site/src/components/LibraryDeclaration';
import library from '@oxsts-library/semantifyr.oxsts';

# Classes

A `class` describes a part of the modeled system. Classes have state ([variables](variables.md)), structure ([features](features.md)), and behavior ([transitions](transitions.md), [properties](properties.md)).

## The `Anything` Base Class

Every class declared in Semantifyr implicitly extends `Anything` from the [standard library](standard-library.md). `Anything` provides empty `init` and `tran` transitions:

<LibraryDeclaration library={library} name="Anything" />

A class has one [`init`](transitions.md), one [implicit `tran`](transitions.md#the-implicit-main-and-init) (named `main`) and one anonymous [`prop`](properties.md), so declaring your own overrides the inherited one, with or without `redefine`; a second one of a kind in the same class is a duplicate name (`OXSTS.DUPLICATE_NAME`). See [Redefining members](#redefining-members).

## Class Declaration

A class declaration consists of the `class` keyword, a name, an optional superclass, and a body in `{ }`. Members may appear in any order.

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

`abstract class` cannot be instantiated directly, so it cannot be a `@VerificationCase` (`OXSTS.ABSTRACT_VERIFICATION_CASE`). An abstract class may declare abstract members (without bodies) that concrete subclasses must redefine. A concrete `@VerificationCase` declares or inherits the anonymous `prop` it checks (`OXSTS.MISSING_PROPERTY`).

```oxsts
abstract class Shape {
  abstract prop area(): int
}
```

## Inheritance

A class extends one superclass, listed after `:`.

```oxsts
abstract class Shape {
  abstract prop area(): int
}

class Square : Shape {
  var side: int := 1

  redefine prop area(): int {
    return side * side
  }
}
```

:::caution
The grammar accepts several superclasses separated by commas, but a class has one superclass: the validator rejects every superclass after the first (`OXSTS.MULTIPLE_INHERITANCE`).
:::

## Redefining Members

`redefine` overrides an inherited member of the **same name**. A redefined [transition](transitions.md) or [property](properties.md) takes as many parameters as the inherited one (`OXSTS.REDEFINITION_SIGNATURE_MISMATCH`), and a redefined property's return type or feature's type must be assignable to the inherited one (`OXSTS.TYPE_MISMATCH`); parameter types may differ. Variables cannot be redefined. Two members of one class that redefine the same inherited member are rejected (`OXSTS.DUPLICATE_REDEFINITION`). Redefining is the primary mechanism by which semantic libraries specialize behavior.

```oxsts
// hide-start
class Target {
  tran activate() {
  }
}

class Transition {
  refers target: Target

  tran fire() {
  }
}

class Jump : Transition {
// hide-end
redefine tran fire() {
  inline target.activate()
}
// hide-start
}
// hide-end
```

```oxsts
// hide-start
class Expression {
  prop evaluate(): int {
    return 0
  }
}

class Literal : Expression {
  var value: int := 0

// hide-end
redefine prop evaluate(): int {
  return value
}
// hide-start
}
// hide-end
```

```oxsts
// hide-start
class Action

class SendAction : Action

class State {
  contains entryAction: Action
}

class SendingState : State {
// hide-end
redefine contains entryAction: SendAction
// hide-start
}
// hide-end
```

## `self`

Inside a class body, `self` refers to the current instance of that class or [feature](features.md). It is typically used to pass the current instance as an argument, or to reach a member whose name a parameter or a local variable reuses. Outside a class, in a global feature or a record, `self` is rejected (`OXSTS.SELF_OUTSIDE_CLASS`).

```oxsts
// hide-start
class Group {
  tran register(member: Counter) {
  }
}

// hide-end
class Counter {
  contains parent: Group

  redefine init {
    inline parent.register(self)
  }
}
```
