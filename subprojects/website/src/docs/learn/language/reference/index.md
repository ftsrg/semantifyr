---
title: Semantifyr Language Reference
sidebar_label: Overview
sidebar_position: 0
description: The reference of the Semantifyr Language, one construct a page.
---

# Semantifyr Language Reference

This reference describes the Semantifyr Language.

## Basic Syntax

This small model shows the main constructs of the language:

```oxsts
package example::lights // the package this file belongs to

class Lamp {
  var lit: bool := false // a variable, part of the state

  tran toggle() { // a transition changes the state
    lit := !lit
  }

  prop isLit(): bool { // a property computes a value
    return lit
  }
}

@VerificationCase // a verification case
class LampCanBeLit {
  contains lamp: Lamp // a feature: the case contains a lamp

  redefine tran { // the main step of the model
    inline lamp.toggle()
  }

  redefine prop { // the verified property
    return EF lamp.isLit()
  }
}
```

## Compile Time and Run Time

Every expression is evaluated in one of two phases. The compiler evaluates some expressions when it [compiles a class](../compilation/index.mdx). The backend evaluates the rest as the model runs. This buffer has a condition of each kind:

```oxsts
class Buffer {
  refers capacity: int = 2
  var count: int := 0

  redefine tran {
    inline if (capacity > 0) { // decided at compile time
      if (count < capacity) { // decided as the model runs
        count := count + 1
      }
    }
  }
}
```

The compiler evaluates multiplicities, feature bindings, the conditions and ranges of `inline` operations, clock bounds, annotation arguments, and the initial values of the variables of classes and the fields of records. The backend evaluates every other expression.

An expression that the compiler evaluates cannot read the state, so `inline if (count > 0)` is an error.
