---
title: 'Direct Modeling'
sidebar_position: 1
---

import LiveEmbed from '@site/src/components/LiveEmbed';
import step1 from '@oxsts-test-models/tutorial/basics-step1.oxsts';
import step2 from '@oxsts-test-models/tutorial/basics-step2.oxsts';
import step3 from '@oxsts-test-models/tutorial/basics-step3.oxsts';
import step4 from '@oxsts-test-models/tutorial/basics-step4.oxsts';
import basics from '@oxsts-test-models/tutorial/basics.oxsts';

# Direct Modeling

This is the entry-level tutorial for Semantifyr. It walks through modeling a small traffic-light system as a single class with the entire state machine inline, and specifies verification cases using safety and reachability properties. There are no reusable abstractions in this tutorial; the [Building a Library](building-a-library.md) tutorial refactors the same example into something more general.

## Requirements

A browser is enough. Every step of this tutorial ends in a complete model: the **Open this step in the editor** button below a code block loads the model of that step into a live editor embedded in this page, where you can edit it and verify its cases. The same editor runs standalone at [live.semantifyr.org](https://live.semantifyr.org). See [Getting Started](../../getting-started.md) for the other ways to run Semantifyr.

:::tip Finished example
The completed model is at the end of this page under [Snapshot](#snapshot). The standalone live editor also offers it in its model picker as *Tutorial: Basics*.
:::

## The Live Editor

The embedded editor stacks three parts:

- The **editor** is a Monaco-based code editor with syntax highlighting, diagnostics (red underlines for errors), and content assist (triggered with `Ctrl+Space`). The language server validates the model as you type.
- The **side panel** lists running and recent verifications on its *Verifications* tab. After a verification that produced a witness, its *Witness* tab shows the witness in three views: *Trace* lists the traced calls of each step, *State* the variable values of each step, and *Raw* the witness as an OXSTS model. In a wide window, such as the standalone editor, the side panel sits to the right of the editor.
- The **Verification Cases** panel lists the verification cases of the model and updates as you type. A circle means not yet verified, a green check mark means passed, and a red cross means failed. The play button at the end of a row verifies that case, the button with a list and a play arrow in the panel header verifies all of them, and the crosshair button jumps to the case in the editor. Once a case has a witness, a document button in its row opens it. With *auto-validate* on, every witness is checked by a second verification, shown as *validated* next to the verification time.

The standalone editor adds a toolbar with a model picker (language and example), a **Copy link** button that encodes your model into a shareable URL, and links to this documentation and to GitHub.

## The Traffic Light

Every Semantifyr file starts with its package. The language uses double colons `::` to separate the parts of a qualified name. Each step of this tutorial is a separate file, so each step gets its own package below `example::trafficlight::basics`.

The simple traffic light has three possible colors: red, yellow, and green. Declare a top-level enumeration of the colors:

<LiveEmbed code={step1} show={['package', 'LightColor']} />

Enums are one of the simplest user-definable types. In this case, the type `LightColor` specifies the `Red`, `Yellow`, and `Green` literal values.

:::note
The language supports semicolons `;`, however, their use is entirely optional.
:::

Next, declare the `TrafficLight` class:

<LiveEmbed code={step1} show={['TrafficLight']} />

The class contains a single **variable** of type `LightColor` that starts with the value `LightColor::Red`, the qualified name of the `Red` **enum literal**. Variables specify the _state_ of the model.

To specify _behavior_ we need **transitions**. Every class implicitly extends the `Anything` base class, which declares an empty `init` transition (run once at startup) and an empty _main_ transition (run on every step). Writing `tran` without a name declares the main transition, and since `TrafficLight` inherits one, it has to `redefine` it. See the [language reference](../../language/transitions.md) for details.

The main transition is a non-deterministic `choice` of three branches. Each branch uses an `assume` to state its precondition (the color the light must currently have) and an assignment `:=` to set the next color. Only a branch whose `assume` holds in the current state can fire, so the model behaves like a state machine that cycles `Red`, `Green`, `Yellow`, `Red`.

Open the step in the editor and try content assist or introduce a typo to see a diagnostic. The verification panel stays empty: the model has no verification case yet.

## Verification Cases

The main functionality of Semantifyr is exposed through classes _annotated_ with `@VerificationCase`, similar to a `@Test` annotation in JUnit. To make the traffic light a verification case, annotate it and add a _property_ for the verifier to check:

<LiveEmbed code={step2} show={['TrafficLight']} />

The anonymous `prop` is the property the verifier checks. Its body uses a temporal operator (see the [language reference](../../language/expressions.md#temporal-operators)): `EF` ("exists finally") is the reachability operator, so the property states that there is at least one execution of the system in which `color` takes the value `LightColor::Yellow`. Its sibling `AG` ("always globally") is the safety operator we will use later in this tutorial.

The `@Tag` annotation attaches a free-form label to a verification case. The tutorial models carry `expect-pass` or `expect-fail`, the verdict we expect, and Semantifyr's own test suite checks every tutorial model against these tags. Your own models do not need them.

**Task:** Open the step in the editor. The verification panel lists `TrafficLight`. Click the play button at the end of its row.

**Check:** While the verification runs, the row shows a spinner. When it finishes, a green check mark appears: the property holds. Click the document button that appeared in the row to open the witness and switch to *State*: the witness is an execution that drives `color` from `Red` through `Green` to `Yellow`, which proves the `EF` property.

## Helper Transitions

Transitions can also structure code by extracting it into reusable parts. Extract the color check and the color assignment into separate transitions:

<LiveEmbed code={step3} show={['TrafficLight']} />

`assumeColor` is a parameterized transition that assumes the current color matches the given argument. The `color` parameter shadows the `color` variable, so the body uses `self.color` to refer to the variable; the `self` keyword always refers to the current instance. `setColor` assigns the parameter to the variable, with the same `self.color` qualifier for the same reason.

The main transition now `inline`s these helpers in each branch of the `choice`. An `inline` call substitutes the body of the called transition at the call site, with each parameter replaced by the corresponding argument.

**Task:** Open the step and verify `TrafficLight` again.

**Check:** It still passes: the refactoring did not change the behavior.

## Features and Composition

Variables define the _state_ of the system, transitions define the _behavior_ of the system, and **features** define the _structure_ of the system. The simplest feature is a containment feature: every instance of the class owns a fixed number of instances of the feature's type.

Take the verification case off the traffic light (remove `@VerificationCase`, the `@Tag` and the `prop` from `TrafficLight`, so that it goes back to a plain class) and add a separate verification case that contains a light:

<LiveEmbed code={step4} show={['GreenColorIsReachable']} />

Every `GreenColorIsReachable` instance contains exactly one `TrafficLight` instance, accessible through the `light` feature; `[1]` is the multiplicity. The case's main transition lets the light take a step by inlining the light's main transition, `light.main()`. See the [features reference](../../language/features.md) for more on containment.

The benefit of the compositional approach is the ability to reuse parts of the model. A real intersection has more than one traffic light. Add a class that composes two lights:

<LiveEmbed code={step4} show={['Intersection']} />

The intersection contains two named lights. Without a multiplicity, a feature holds exactly one instance, as if written `[1]`. The main transition of `Intersection` non-deterministically lets one light or the other take a step. Each light is its own complete state machine; the intersection chooses which one runs on a given step.

The crucial question for any intersection is whether the two lights can ever be green at the same time. Add a verification case that asks exactly that:

<LiveEmbed code={step4} show={['IntersectionNeverBothGreen']} />

This is an `AG` safety property: on every execution, the two lights are never green at the same time. The tag already gives away what we expect.

**Task:** Open the step in the editor. The verification panel now shows `GreenColorIsReachable` and `IntersectionNeverBothGreen`. Click the button with a list and a play arrow in the panel header to verify both.

**Check:** `GreenColorIsReachable` passes, and `IntersectionNeverBothGreen` fails with a red cross; the editor also marks its property with an error. A failed safety property comes with a counterexample: open its witness and switch to *State* to see the steps that drive both lights to `Green`, one after the other.

## Fixing the Intersection

To coordinate the lights, the intersection needs a way to ask whether a light is currently green. Add a property to `TrafficLight`, after the main transition:

<LiveEmbed code={basics} show={['TrafficLight']} />

`isColor` is a named property that takes a color as a parameter and returns a `bool`. Properties are pure functions of the state; unlike transitions, they cannot change it. As with `assumeColor` and `setColor`, the parameter shadows the variable, so the body uses `self.color`.

Next, guard each branch of the intersection's `choice` so that a light only steps when the other light is not green:

<LiveEmbed code={basics} show={['Intersection']} />

Now a light can only take a step when the other light is not green. As soon as one light turns `Green`, the other one cannot move until the green light has moved on to `Yellow`. In the finished model, both verification cases carry `@Tag("expect-pass")`.

**Task:** Open the step and verify all cases again.

**Check:** Both cases show a green check mark. The safety issue is fixed.

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

The complete final model:

<LiveEmbed code={basics} />
