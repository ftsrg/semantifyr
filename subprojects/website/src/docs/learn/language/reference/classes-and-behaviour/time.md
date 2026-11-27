---
title: Time
slug: /learn/language/reference/time
sidebar_position: 8
description: The time of the Semantifyr Language, its clocks, the wait before every main step, deadlines, and closed clock tests.
---

# Time

Before each main step, time passes by any amount, including zero and fractions. The step itself takes no time, and neither does `init`. Time has no unit. A model reads time through clocks.

## Clocks

Declare a clock as a variable of type `clock`. A clock starts at 0 and grows as time passes. Reset it by assigning 0, and test it by comparing it with an integer:

```oxsts
class Lamp {
  var lit: bool := false
  var litFor: clock := 0

  redefine tran {
    choice {
      assume(!lit)
      lit := true
      litFor := 0
    } or {
      assume(lit && litFor >= 3) // lit for at least 3
      lit := false
    }
  }
}
```

A clock is always a single variable of a class. The integer it is compared with is computed at compile time, so it can be a constant or a [bound feature](features.md#feature-binding), but not a variable.

A clock can only be reset and compared. You cannot havoc it, compute with it, compare it with another clock, or read it in the verification property:

```oxsts issue=OXSTS.INCORRECT_CLOCK_USE
class Lamp {
  var litFor: clock := 0

  redefine tran {
    havoc(litFor) // an error
  }
}
```

## The Wait Before a Step

Each main step chooses how long to wait before it runs. The `assume` operations on clocks limit that choice. This blinker switches after waiting at least 2 and at most 5:

```oxsts
class Blinker {
  var on: bool := false
  var since: clock := 0

  redefine tran {
    assume(since <= 5) // waited at most 5
    assume(since >= 2) // waited at least 2
    on := !on
    since := 0
  }
}
```

Each `assume` reads the clocks after the wait and after any reset earlier in the same step. An upper bound, such as `since <= 5`, is a deadline: the step waits at most that long. A deadline that has passed can leave the model with no next step.

## Closed Clock Tests

A clock test is closed if it is in an `assume` and uses `<=`, `>=`, or `==`. The validator warns about tests that may not be closed:

```oxsts issue=OXSTS.CLOCK_TEST_NOT_CLOSED
class Lamp {
  var lit: bool := false
  var litFor: clock := 0

  redefine tran {
    if (litFor < 3) { // a warning
      lit := true
    } else {
      lit := false
      litFor := 0
    }
  }
}
```

A test that is not closed is not an error. The [backend pages](../../../backends/index.mdx) say how each backend handles a model with such a test.
