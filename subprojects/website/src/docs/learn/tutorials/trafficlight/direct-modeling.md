---
title: 'Direct Modeling'
sidebar_position: 1
---

# Direct Modeling

This is the entry-level tutorial for Semantifyr. It walks through modeling a small traffic-light system as a single class with the entire state machine inline, and specifies verification cases using safety and reachability properties. There are no reusable abstractions in this tutorial; the [Building a Library](building-a-library.md) tutorial refactors the same example into something more general.

## Requirements

You can follow this tutorial in any Semantifyr environment. The quickest way is to use the **live editor** directly in your browser: [Open the live editor](https://live.semantifyr.org).
No setup required. See [Getting Started](../../getting-started.md) for other options like the JupyterHub environment.

:::tip Finished example
If you want to skip ahead and see the completed model, use the **Load example** button in the toolbar and select *Tutorial: traffic light (direct modeling)*.
:::

## The Live Editor

If you are using the live editor, here is a quick overview of the interface:

- The **toolbar** at the top contains the Semantifyr logo, a connection indicator, a **Load example** button, a **Copy link** button, and links to the documentation and GitHub.
- The **editor** in the center is a Monaco-based code editor with syntax highlighting, diagnostics (red underlines for errors), and content assist (triggered with `Ctrl+Space`).
- The **verification panel** at the bottom lists verification cases found in your code. It updates automatically as you type. Each case shows its status: a small circle means not yet verified, a check mark means passed, and an X means failed.
- The **status bar** at the very bottom shows the current connection state and activity messages.

You can start typing directly in the editor. The language server connects automatically and provides feedback as you write.

## Initial Traffic Light Model

If you are using the live editor, start with an empty editor. In a local environment, create a new file called `trafficlight.oxsts`. Add a single line at the top:

```oxsts
package example::trafficlight::basics
```

:::note
The language supports semicolons `;`, however, their use is entirely optional.
:::

Every Semantifyr file specifies its own package, `example::trafficlight` in this case. The language uses double colons `::` to denote fully-qualified identifiers.

The simple traffic light has three possible colors: red, yellow, and green. Declare a top-level enumeration of the colors:

```oxsts
enum LightColor {
    Red,
    Yellow,
    Green
}
```

Enums are one of the simplest user-definable types. In this case, the type `LightColor` specifies the `Red`, `Yellow`, and `Green` literal values.

Next, declare the `TrafficLight` class:

```oxsts
class TrafficLight {
    var color: LightColor := LightColor::Red
}
```

Initially, it contains a single **variable** of type `LightColor` _bound_ to the value `LightColor::Red`, which is the fully-qualified name of the `Red` **enum literal**.

Variables specify the _state_ of the model. To specify _behavior_ we must introduce **transitions**. The most basic transition is the _main_ transition.

```oxsts
class TrafficLight {
    var color: LightColor := LightColor::Red

    redefine tran {
        choice {
            assume (color == LightColor::Red)
            color := LightColor::Green
        } or {
            assume (color == LightColor::Green)
            color := LightColor::Yellow
        } or {
            assume (color == LightColor::Yellow)
            color := LightColor::Red
        }
    }
}
```

:::note
Every class implicitly inherits from the `Anything` base class, which declares the base redefinable `init` and `main` transitions. See the [language reference](../../language/transitions.md) for more details.
:::

Here we _redefined_ the _main_ transition with a non-deterministic `choice` made up of three branches. Each branch uses an `assume` to declare its precondition (the current color the light must be in) and an assignment to set the next color. Only the branch whose `assume` succeeds in the current state can fire, so the model behaves like a state machine that cycles `Red`, `Green`, `Yellow`, `Red`.

Transitions can also be used to structure and simplify code by extracting it into reusable parts. Let's extract the color matching and the color assignment into separate transitions.

```oxsts
class TrafficLight {
    // var color as before

    tran assumeColor(color: LightColor) {
        assume (self.color == color)
    }

    tran setColor(color: LightColor) {
        self.color := color
    }

    redefine tran {
        choice {
            inline assumeColor(LightColor::Red)
            inline setColor(LightColor::Green)
        } or {
            inline assumeColor(LightColor::Green)
            inline setColor(LightColor::Yellow)
        } or {
            inline assumeColor(LightColor::Yellow)
            inline setColor(LightColor::Red)
        }
    }
}
```

We introduced two helper transitions here. `assumeColor` is a parameterized transition that asserts the current color matches the given argument. The `color` parameter shadows the `color` variable: to differentiate we can use `self.color` to refer to the variable instead of the parameter. The `self` keyword always refers to the current instance. The `setColor` transition is a parameterized transition that assigns the parameter to the variable, using the same `self.color` qualifier for the same reason.

The redefined main transition now `inline`s these helpers in each branch of the `choice`. The `inline` call substitutes the body of the called transition at the call site, with each parameter replaced by the corresponding argument.

## Verification Cases

The main functionality of Semantifyr is exposed through classes _annotated_ with the `VerificationCase` annotation, which is similar to a `Test` annotation in `JUnit` for example. To make our model into a verification case, we add the annotation and a _property_ that the verifier will check.

Add the annotation to the existing `TrafficLight` class and give it a `prop`:

```oxsts
@VerificationCase
class TrafficLight {
    // add the following property

    prop {
        return EF color == LightColor::Yellow
    }
}
```

The body uses a temporal operator (see the [language reference](../../language/expressions.md#temporal-operators)). Here it specifies that there is at least one execution of the system in which the `color` variable takes the `LightColor::Yellow` value. `EF` ("exists finally") is the reachability operator; its sibling `AG` ("always globally") is the safety operator we will use later in this tutorial.

After annotating the class, the **verification panel** at the bottom of the editor discovers it automatically. You should see a new entry appear in the panel:

- The case starts with a small circle icon, meaning it has not been verified yet.
- Click the **play button** next to the case name to verify it, or use the **play-all button** in the panel header to run all cases at once.
- While verification is running, the case icon changes to a spinner and the status bar shows progress messages.
- When it finishes, a green check mark appears if the property holds, or a red X if it does not. Error messages are shown inline below the case name.
- You can click a case name to jump to its definition in the editor. The **refresh button** in the panel header re-discovers cases if the panel gets out of sync.

Run the verification now. It should succeed: the verifier finds a witness trace that drives `color` from `Red` to `Green` to `Yellow`, confirming the `EF` reachability property.

## Features and Composition

Variables define the _state_ of the system, transitions define the _behavior_ of the system, and **features** define the _structure_ of the system. The simplest feature is a containment feature, which specifies that all instances of the encompassing class own a fixed number of child instances of the feature type.

Let's extract the traffic light logic and instantiate it from a separate verification case. Start by removing the `@VerificationCase` annotation and the implicit `prop` from `TrafficLight` (so that it goes back to its previous form), then add a new verification case below it:

```oxsts
@VerificationCase
class GreenColorIsReachable {
    contains light: TrafficLight[1]

    redefine tran {
        inline light.main()
    }

    prop {
        return EF (light.color == LightColor::Green)
    }
}
```

This declares that every `GreenColorIsReachable` instance must contain exactly one `TrafficLight` instance, accessible through the `light` feature. See the [features reference](../../language/features.md) for more details on containment.

The benefit of the compositional approach is the ability to reuse parts of the model. A real intersection has more than one traffic light. Add a new class that composes two lights:

```oxsts
class Intersection {
    contains north: TrafficLight // [1] is the implicit multiplicity
    contains east: TrafficLight

    redefine tran {
        choice {
            inline north.main()
        } or {
            inline east.main()
        }
    }
}
```

The intersection contains two named lights. The implicit `tran` of `Intersection` non-deterministically lets one light or the other take a step. Each light is its own complete state machine; the intersection chooses which one runs on a given step.

The crucial question for any intersection is whether the two lights can ever be green at the same time. Add a verification case that asks exactly that:

```oxsts
@VerificationCase
class IntersectionNeverBothGreen {
    contains intersection: Intersection

    redefine tran {
        inline intersection.main()
    }

    prop {
        return AG !(intersection.north.color == LightColor::Green && intersection.east.color == LightColor::Green)
    }
}
```

Here we used an `AG` safety property, which specifies that the two lights' colors must not both be green on any execution.

**Task:** Run the verifier on `IntersectionNeverBothGreen`. In the live editor, the verification panel now shows both `GreenColorIsReachable` and `IntersectionNeverBothGreen`. Click the play button next to `IntersectionNeverBothGreen` to run it individually, or use the play-all button in the panel header to run all cases.

**Check:** The verification case fails: a red X appears next to it in the panel. The counter-example is a sequence of steps that drives both lights to `Green` at the same time, violating the `AG` safety property.

To coordinate the lights, the intersection needs a way to ask whether a specific light is currently green (or any other color). Add a small property to `TrafficLight`, after the `redefine tran` block:

```oxsts
prop isColor(color: LightColor): bool {
    return self.color == color
}
```

Here we declared a property called `isColor` that takes a color as a parameter and returns a `bool` value. As with `assumeColor` and `setColor`, the parameter name `color` shadows the class variable, so the body uses `self.color` to refer to the variable.

Next, guard each branch of the intersection's `choice` so that a light only steps when the other light is not currently green. Replace `Intersection` with:

```oxsts
class Intersection {
    contains north: TrafficLight
    contains east: TrafficLight

    redefine tran {
        choice {
            assume (!east.isColor(LightColor::Green))
            inline north.main()
        } or {
            assume (!north.isColor(LightColor::Green))
            inline east.main()
        }
    }
}
```

Now a light can only take a step when the other light is not green. As soon as one light enters `Green`, the other is blocked from advancing into `Green` itself, and must wait for the green light to move on to `Yellow`.

**Task:** Re-run the verifier on `IntersectionNeverBothGreen`. Note that after editing the code, the verification panel marks all cases as stale (small circle icon) since the results may no longer be valid.

**Check:** The case now shows a green check mark. The safety issue has been fixed. If you run all cases, both `GreenColorIsReachable` and `IntersectionNeverBothGreen` should pass.

## Wrap-up

This tutorial introduced some of the most basic language constructs:

- Packages
- Enum declarations
- Class declarations
- Variables for specifying the state
- Transitions for specifying the behavior
- Features for specifying the structure
- Properties for querying state
- Verification cases

For deeper details on any of these constructs, see the [language reference](../../language/index.md).

The next tutorial in the series, [Building a Library](building-a-library.md), extends this model by refactoring the common parts (states and transitions) into reusable _library_ elements.

## Snapshot

The complete final state of `trafficlight.oxsts`:

```oxsts verify open=trafficlight-direct-snapshot
package example::trafficlight

enum LightColor {
    Red,
    Yellow,
    Green
}

class TrafficLight {
    var color: LightColor := LightColor::Red

    tran assumeColor(color: LightColor) {
        assume (self.color == color)
    }

    tran setColor(color: LightColor) {
        self.color := color
    }

    redefine tran {
        choice {
            inline assumeColor(LightColor::Red)
            inline setColor(LightColor::Green)
        } or {
            inline assumeColor(LightColor::Green)
            inline setColor(LightColor::Yellow)
        } or {
            inline assumeColor(LightColor::Yellow)
            inline setColor(LightColor::Red)
        }
    }

    prop isColor(color: LightColor): bool {
        return self.color == color
    }
}

class Intersection {
    contains north: TrafficLight
    contains east: TrafficLight

    redefine tran {
        choice {
            assume (!east.isColor(LightColor::Green))
            inline north.main()
        } or {
            assume (!north.isColor(LightColor::Green))
            inline east.main()
        }
    }
}

@VerificationCase
class GreenColorIsReachable {
    contains light: TrafficLight[1]

    redefine tran {
        inline light.main()
    }

    prop {
        return EF (light.color == LightColor::Green)
    }
}

@VerificationCase
class IntersectionNeverBothGreen {
    contains intersection: Intersection

    redefine tran {
        inline intersection.main()
    }

    prop {
        return AG !(intersection.north.color == LightColor::Green && intersection.east.color == LightColor::Green)
    }
}
```
