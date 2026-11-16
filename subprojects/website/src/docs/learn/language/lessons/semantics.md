---
title: 'Changing the Semantics'
sidebar_position: 3
description: The third lesson of the Semantifyr Language. Write once, in the statechart library, the rule that decides which transition fires, then change the rule and watch the verdicts of models you did not touch change.
---

import LiveEmbed from '@site/src/components/LiveEmbed';
import CaseVerdict from '@site/src/components/CaseVerdict';
import library from '@served-examples/oxsts/library';
import semantics from '@served-examples/oxsts/semantics';
import variant from '@served-examples/oxsts/semantics-step1';

# Changing the Semantics

**What you verify:** that the lights of [A Statechart Library](library.md) keep their verdicts when the rule that fires their transitions moves into the library, and which of their verdicts change when you change that rule.

In the second lesson every light ended with the same main transition: a choice of its transitions. That is a rule of the statechart language, and a library says it once. Then the library is the meaning of every model written on it: change the library, and the same models mean something else.

This lesson starts from the model the second lesson ended in. Open it:

<LiveEmbed example={library} />

## One Rule for Every Statechart

A class does not know which instances its features will hold. [`inline for choice`](../specification/classes-and-behaviour/inline-operations.md#inline-for-choice) runs its body for one instance of a feature, any of them, the way a `choice` takes one branch:

```oxsts
class Lamp {
    var on: bool := false

    tran toggle() {
        on := !on
    }
}

class Lamps {
    contains lamps: Lamp[0..*]
    contains left: Lamp subsets lamps
    contains right: Lamp subsets lamps

    redefine tran {
        inline for choice (lamp in lamps) {
            inline lamp.toggle()
        }
    }
}
```

When the model is compiled, the loop becomes a `choice` with a branch for each lamp, so a step of `Lamps` toggles either `left` or `right`; [inlining](../compilation/inlining.mdx) shows how. Write the statechart's rule once:

- `Statechart`'s main transition fires one of its transitions, whichever can fire, for every statechart on the library.
- `TrafficLight` and `PedestrianLight` have no main transition of their own.

<details>
<summary>The verdicts to expect</summary>

- <CaseVerdict example={semantics} name="LightCanTurnYellow" />
- <CaseVerdict example={semantics} name="EastCanTurnGreen" />
- <CaseVerdict example={semantics} name="NeverBothGreen" />
- <CaseVerdict example={semantics} name="NorthGreenOnlyWhileEastRed" />
- <CaseVerdict example={semantics} name="PedestriansCanWalk" />
- <CaseVerdict example={semantics} name="WalkOnlyWhileRed" />

</details>

The lights are data now: states, transitions and where they start. Where does `redToGreen` get its meaning from?

<details>
<summary>Solution</summary>

<LiveEmbed example={semantics} />

</details>

## Another Rule

[`inline for`](../specification/classes-and-behaviour/inline-operations.md#inline-for-sequence) runs its body for every instance of a feature, one after the other, in the order the instances are declared, as one sequence:

```oxsts
class Lamp {
    var on: bool := false

    tran toggle() {
        on := !on
    }
}

class Lamps {
    contains lamps: Lamp[0..*]
    contains left: Lamp subsets lamps
    contains right: Lamp subsets lamps

    redefine tran {
        inline for (lamp in lamps) {
            inline lamp.toggle()
        }
    }
}
```

A step of these `Lamps` toggles `left`, then `right`. Some statechart languages fire more than one transition in a step. Change the rule of the library, and touch no light and no case:

- In each step every transition of a statechart runs, one after the other, in the order the statechart declares them.

Before you verify, predict each of the six verdicts.

<details>
<summary>The verdicts to expect</summary>

- <CaseVerdict example={variant} name="LightCanTurnYellow" />
- <CaseVerdict example={variant} name="EastCanTurnGreen" />
- <CaseVerdict example={variant} name="NeverBothGreen" />
- <CaseVerdict example={variant} name="NorthGreenOnlyWhileEastRed" />
- <CaseVerdict example={variant} name="PedestriansCanWalk" />
- <CaseVerdict example={variant} name="WalkOnlyWhileRed" />

</details>

Follow a traffic light through one step under the new rule, starting from red, transition by transition. Is the crossing safer now?

<details>
<summary>Solution</summary>

<LiveEmbed example={variant} />

</details>

## What You Can Do Now

You can write the rule of a language once, in its library, and change it. No light and no case changed in the last step, and verdicts did: what a model means is in the library it is written on. That is how Semantifyr gives SysML v2 and Gamma their meaning, each with a library of its own, and why every requirement in these lessons came with a case asking that something can happen at all. The lessons go on with numbers, [The Minimum Green Time](green-time.md), and with a richer statechart library, [Hierarchy, Events and the Environment](hierarchy.md).
