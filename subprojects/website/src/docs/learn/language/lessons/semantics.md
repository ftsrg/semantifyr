---
title: 'Changing the Semantics'
sidebar_position: 3
description: The third lesson of the Semantifyr Language. Write once, in the statechart library, the rule that decides which transition fires, then change the rule and see the verdicts of models you did not touch change.
---

import LiveLink from '@site/src/components/LiveLink';
import CaseVerdict from '@site/src/components/CaseVerdict';
import { RuleSteps } from '@site/src/components/LessonFigures';
import library from '@served-examples/oxsts/library';
import semantics from '@served-examples/oxsts/semantics';
import variant from '@served-examples/oxsts/semantics-step1';

# Changing the Semantics

The goal of this lesson is to write a rule of a modeling language once, in its library, and to see that changing the rule changes what every model on the library means.

## Running example

The running example is the junction and the crossing of [A Statechart Library](library.md), with the same requirements: the lights are never both green, a light turns green only while the other one is red, pedestrians walk only while the traffic light is red, and each light can move on. The lesson starts from the final state of that lesson, which can be opened via <LiveLink example={library}>this link</LiveLink>.

Every light there ends with the same main transition: a choice of its transitions, so that one of them fires in a step. That is not a property of a light but a rule of the statechart language, and statechart languages differ in such rules: some fire one transition in a step, others several. In the Semantifyr Language such a rule belongs in the library, written once.

## Writing the rule once

A class does not know which instances its features will hold. [`inline for choice`](../specification/classes-and-behaviour/inline-operations.md#inline-for-choice) runs its body for one instance of a feature, any of them, as a `choice` takes one branch:

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

When the model is compiled, the loop becomes a `choice` with a branch for each lamp, so a step of `Lamps` toggles either `left` or `right`. The [inlining page](../compilation/inlining.mdx) shows how.

- Give `Statechart` a main transition that fires one of its transitions, whichever can fire.
- Remove the main transitions of `TrafficLight` and `PedestrianLight`.
- Verify all. Nothing changed in what the lights do, so every case keeps its verdict: <CaseVerdict example={semantics} name="LightCanTurnYellow" />, <CaseVerdict example={semantics} name="EastCanTurnGreen" />, <CaseVerdict example={semantics} name="NeverBothGreen" />, <CaseVerdict example={semantics} name="NorthGreenOnlyWhileEastRed" />, <CaseVerdict example={semantics} name="PedestriansCanWalk" /> and <CaseVerdict example={semantics} name="WalkOnlyWhileRed" />.
- The lights are now data only: states, transitions and where they start. Where does `redToGreen` get its meaning from?
- This state of the lesson can be opened via <LiveLink example={semantics}>this link</LiveLink>.

## Changing the rule

[`inline for`](../specification/classes-and-behaviour/inline-operations.md#inline-for-sequence) runs its body for every instance of a feature, one after the other, in the order the instances are declared:

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

A step of these `Lamps` toggles `left`, then `right`. Let us change the statechart language so that a statechart fires all of its transitions in a step.

- Change the rule of `Statechart`: in each step every transition of the statechart runs, one after the other, in the order the statechart declares them. Do not touch any light or any case.
- Before you verify, decide the verdict of each of the six cases.
- Verify all. Three reachability cases now fail: <CaseVerdict example={variant} name="LightCanTurnYellow" />, <CaseVerdict example={variant} name="EastCanTurnGreen" /> and <CaseVerdict example={variant} name="PedestriansCanWalk" />. The three cases that require something in every state still pass: <CaseVerdict example={variant} name="NeverBothGreen" />, <CaseVerdict example={variant} name="NorthGreenOnlyWhileEastRed" /> and <CaseVerdict example={variant} name="WalkOnlyWhileRed" />.
- Observe what a traffic light does in one step from red under each rule:

<RuleSteps />

- Is the crossing safer under the new rule?
- This state of the lesson can be opened via <LiveLink example={variant}>this link</LiveLink>.

No light and no case changed, and the verdicts did: what a model means is decided by the library it is written on. This is how Semantifyr gives SysML v2 and Gamma their meaning, each with a library of its own. The lessons go on with numbers in [The Minimum Green Time](green-time.md), and with a richer statechart library in [Hierarchy, Events and the Environment](hierarchy.md).
