---
title: Classes
slug: /learn/language/reference/classes
sidebar_position: 1
description: The classes of the Semantifyr Language, their declaration, inheritance, abstract classes, redefinition, the Anything base class, and member access.
---

import LibraryDeclaration from '@site/src/components/LibraryDeclaration';
import library from '@oxsts-library/standard.semantifyr';

# Classes

A class describes a part of the system you model. It has four kinds of members: [variables](variables.md) (its state), [features](features.md) (its parts), [transitions](transitions.md) (how its state changes), and [properties](properties.md) (values computed from its state).

## Declaring a Class

Declare a class with the `class` keyword, followed by its members in braces:

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

A class without members can leave out the braces:

```oxsts
class Marker
```

## Inheritance

A class extends at most one other class, its superclass. Write the superclass after a colon:

```oxsts
class Counter {
  var value: int := 0
}

class LimitedCounter : Counter {
  var limit: int := 10

  prop atLimit(): bool {
    return value >= limit // value is inherited from Counter
  }
}
```

The subclass inherits all members of its superclass. A class without a superclass extends [`Anything`](#the-anything-base-class).

## Abstract Classes

Mark a class `abstract` when it is only meant to be extended. An abstract class can declare abstract transitions and properties, which have no body:

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

A class that is not abstract must redefine every abstract member it inherits. An abstract class cannot be a verification case.

Only an abstract class can have abstract members:

```oxsts issue=OXSTS.MEMBER_NOT_IN_ABSTRACT_CLASS
class Shape {
  abstract prop area(): int // an error
}
```

## Redefining Members

To replace an inherited member, declare it again with `redefine`:

```oxsts
class Counter {
  var value: int := 0

  tran increment() {
    value := value + 1
  }
}

class DoubleCounter : Counter {
  redefine tran increment() {
    value := value + 2
  }
}
```

A redefinition takes the same number of parameters as the member it replaces. The type of a redefined property or feature must be the inherited type or a more specific one. Variables cannot be redefined.

Without `redefine`, a member with the same name does not replace the inherited one. It only hides its name, and you get a warning:

```oxsts issue=OXSTS.ELEMENT_SHADOWED
class Counter {
  tran increment() {
  }
}

class DoubleCounter : Counter {
  tran increment() { // a warning
  }
}
```

Inherited members that use the name still use the inherited member. A verification case without `redefine` on its `init`, `tran`, or `prop` still runs and checks the inherited ones.

## The `Anything` Base Class

Every class extends `Anything` from the [standard library](../library-and-output/standard-library.md). `Anything` has an empty `init` and `tran`, and a `prop` that always holds:

<LibraryDeclaration library={library} name="Anything" />

A verification case is a class marked [`@VerificationCase`](../library-and-output/standard-library.md#verificationcase). Verifying it runs its [`init`](transitions.md#initial-states) once, then its [`tran`](transitions.md#the-implicit-main-and-init) again and again, and checks its [`prop`](properties.md#the-verification-property). Redefine them to give the case its own behaviour and property.

## Accessing Members

Inside a class, use a member by its name, as in `value`. Use a period to reach a member of another instance, as in `counter.value`. `self` is the current instance, so `self.value` reaches the member even when a parameter has the same name:

```oxsts
class Counter {
  var value: int := 0

  tran set(value: int) {
    self.value := value // self.value is the variable, value is the parameter
  }
}

class Holder {
  contains counter: Counter

  prop current(): int {
    return counter.value
  }
}
```

Reading a member through a variable that holds `nothing` has no value, and the operation [blocks](operations.md#blocking).
