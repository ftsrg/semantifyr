---
title: Properties
slug: /learn/language/reference/properties
sidebar_position: 7
description: The properties of the Semantifyr Language, values computed from the state, with parameters, calls, and the verification property.
---

import Planned from '@site/src/components/Planned';

# Properties

A property computes a value from the state, without changing it. Use properties in conditions and as the verification property.

## Declaring a Property

Declare a property with `prop`, a name, parameters, a return type, and a body with one `return`:

```oxsts
class Counter {
  var value: int := 0

  prop isPositive(): bool {
    return value > 0
  }
}
```

A property can be `abstract`, and a subclass can [redefine](classes.md#redefining-members) it.

## Parameters

Write parameters in parentheses, each with a name and a type:

```oxsts
class Counter {
  var value: int := 0

  prop isAtLeast(threshold: int): bool {
    return value >= threshold
  }
}
```

A parameter of multiplicity `[0..1]` is optional. A call can leave it out, and it is then `nothing`. Optional parameters come after the required ones.

A parameter can also hold an instance, and the body can read its members:

```oxsts
// hide-start
class Worker {
  var done: bool := false
}

// hide-end
class Supervisor {
  prop isDone(worker: Worker): bool {
    return worker.done
  }
}
```

## Calling a Property

Call a property by its name, as in `exceeds(10)`, or on another instance, as in `a.atLimit()`:

```oxsts
class Counter {
  var value: int := 0

  prop exceeds(limit: int): bool {
    return value > limit
  }

  prop atLimit(): bool {
    return exceeds(10)
  }
}

class Pair {
  contains a: Counter
  contains b: Counter

  prop bothAtLimit(): bool {
    return a.atLimit() && b.atLimit()
  }
}
```

You can name arguments, as in `exceeds(limit = 10)`. Named arguments come after the positional ones. Every required parameter needs an argument. A parameter takes at most one argument, by position or by name. Each argument must be assignable to its parameter ([Assignability](../foundations/types.md#assignability)).

To read a member of a call's result, put the call in parentheses, as in `(first()).value`. Without the parentheses, `first().value` is a syntax error.

## The Verification Property

A verification case checks its unnamed property, `prop`. Give a case its own with `redefine prop`:

```oxsts
// hide-start
class Counter {
  var value: int := 0
}

// hide-end
@VerificationCase
class StaysBelow100 {
  contains counter: Counter

  redefine prop {
    return AG counter.value < 100
  }
}
```

The verification property returns `AG e` or `EF e`. `AG e` holds if `e` holds in every reachable state. `EF e` holds if `e` holds in at least one reachable state. `AG` and `EF` can only stand at the start of the expression a property returns, so they cannot be nested or negated.

The verdict is Passed if the property holds, and Failed if it does not ([verdicts](../../../backends/portfolios-and-verdicts.mdx#the-verdict-from-the-answers)).

<Planned>

CTL* properties are planned. Today only `AG` and `EF` are supported.

</Planned>
