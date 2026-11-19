---
title: Time
slug: /learn/language/specification/time
sidebar_position: 11.5
description: The time of the Semantifyr Language, its clocks, the wait before every main step, deadlines and closed clock tests.
---

# Time

Time in the Semantifyr Language is dense and passes between main steps only: before every main step the model waits, any amount of time, zero and fractions included, and the step itself takes no time. A model reads time through clocks.

## Clocks

A clock is a variable of the [standard library's](../library-and-output/standard-library.md#datatypes) datatype `clock`. It starts at 0, advances by the wait before every main step, and is reset to 0 by assignment. It is read only in a comparison with an integer the compiler can evaluate, with every comparison operator and its negation.

```oxsts
class Lamp {
  var lit: bool := false
  var litFor: clock := 0

  redefine tran {
    choice {
      assume (!lit)
      lit := true
      litFor := 0
    } or {
      assume (lit && litFor >= 3)
      lit := false
    }
  }
}
```

A clock is a variable of a class, never a local variable, a record field, a feature or a parameter, and it holds exactly one value, so it is no array and not optional. The validator rejects arithmetic on a clock, a `havoc` of a clock, an assignment of anything but 0, a comparison of two clocks and a clock passed as an argument, and the verified property cannot read a clock, also not through a property it calls: a timing requirement is modelled as a value that a timed branch sets. A bound that reads the state is rejected once the model is inlined. A clock read through a variable that holds an instance is the clock of the instance it holds.

```oxsts issue=OXSTS.INCORRECT_CLOCK_USE
class Lamp {
  var litFor: clock := 0

  redefine tran {
    havoc(litFor)
  }
}
```

## The Wait Before a Step

A main step is a wait followed by the main [transition](transitions.md). The wait has no statement in the model: its amount is a choice of the step, every clock advances by it, and then the transition runs. `init` takes no time.

The step's `assume` operations over clocks prune the choice, each reading the clocks as they stand when it runs, after the wait and any reset before it. An `assume` that bounds a clock from above is a deadline: on the paths that run it, the step waits only as long as the clock stays within the bound. A step that assumes `c <= 0` for a clock the step before it reset takes no time; a reset earlier in the same step does not bound the wait, since the `assume` then reads the clock after the reset.

```oxsts
class Blinker {
  var on: bool := false
  var since: clock := 0

  redefine tran {
    assume (since <= 5)
    assume (since >= 2)
    on := !on
    since := 0
  }
}
```

The blinker switches in every step, each after a wait of at least 2 and at most 5: the first `assume` is a deadline, so no step waits past 5, and the second keeps out every wait shorter than 2.

A wait may be zero, even in every step. A passed deadline can leave a state with no next step. A clock has no unit; the model decides it.

## Closed Clock Tests

A clock test is closed when it stands in an `assume`, is not negated once the condition is in negation normal form, and compares with `<=`, `>=` or `==`. A test of a clock that is reset on every path before it in the same step, and every test in `init`, is closed too. The validator warns of a clock test that may not be closed, judging each transition alone; whether every test is closed is decided on the inlined model, and a test that is not closed is no error: the model means what it says. Whether a backend trusts its proof of such a model is on [the pages of the backends](../../../backends/index.mdx).

```oxsts issue=OXSTS.CLOCK_TEST_NOT_CLOSED
class Lamp {
  var lit: bool := false
  var litFor: clock := 0

  redefine tran {
    if (litFor < 3) {
      lit := true
    } else {
      lit := false
      litFor := 0
    }
  }
}
```

## Time in a Trace

The [report](../../../reference/artifacts.mdx#the-report) of a run states the time that passed before each step, and the clocks' values in each state, as exact fractions. The [witness](../library-and-output/inlined-models.md#witnesses) of a run with clocks assumes them after each step.
