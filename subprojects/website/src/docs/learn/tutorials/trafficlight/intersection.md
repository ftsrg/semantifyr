---
title: 'The Intersection'
sidebar_position: 2
description: The second OXSTS lesson. Put two traffic lights at an intersection, verify that they are never green together, and find with a second case what the first did not ask.
---

import LiveEmbed from '@site/src/components/LiveEmbed';
import CaseVerdict from '@site/src/components/CaseVerdict';
import step1 from '@served-examples/oxsts/basics-step1';
import step2 from '@served-examples/oxsts/basics-step2';
import basics from '@served-examples/oxsts/basics';

# The Intersection

**What you verify:** that two lights at an intersection are never green together, that they still switch, and, with a case you add, a requirement the first case does not cover.

This lesson starts from the light of [One Light](one-light.md).

## Two Lights

The light is no longer a case itself. It gains a named [property](../../language/properties.md), `isColor`, which tells whether the light shows a color:

<LiveEmbed example={step1} show={['TrafficLight']} />

The parameter `color` has the name of the variable, so the body reaches the variable as `self.color`; [`self`](../../language/expressions.md#self) is the light the property is asked of. Ignore `@Trace` until the trace below.

The intersection [contains](../../language/features.md#contains) two lights, `north` and `east`, each a light of its own. In each step one of them switches: `inline north.main()` runs north's main transition in the intersection's step, an [inline call](../../language/inline-operations.md#inline-call). The main transitions of contained lights run only where a main transition calls them; the verifier runs the case's alone.

Two lights must not be green together, so a light switches only while the other is not green:

<LiveEmbed example={step1} show={['Intersection']} />

## Never Both Green

The first case contains an intersection, steps it, and asks with [`AG`](../../language/expressions.md#temporal-operators), "always globally", whether the two lights are never green at the same time, in every state of every execution. The second asks whether east can turn green at all:

<LiveEmbed example={step1} show={['NeverBothGreen', 'EastCanTurnGreen']} />

A model that never moves would answer the first question just as well as a correct one. The second makes sure the lights still switch.

Predict both verdicts, then press **Run** and **Verify all**:

<details>
<summary>The verdicts Live shows</summary>

- <CaseVerdict example={step1} name="NeverBothGreen" />
- <CaseVerdict example={step1} name="EastCanTurnGreen" />

</details>

An `AG` case that holds has no execution to show: no state breaks it. Open the trace of `EastCanTurnGreen`. `@Trace` on the light's main transition fills the *Calls* view: each step names the light that switched, `root.intersection.north` or `root.intersection.east`, beside the colors in *Steps*.

## A Second Requirement

At a real intersection a light turns green only once the crossing traffic has stopped, that is, while the other light is red. Write that down as a case, for north:

<LiveEmbed example={step2} show={['NorthGreenOnlyWhileEastRed']} />

`!a || b` reads "if a, then b". Does the guard of the intersection make it hold? Decide, then run the step:

<details>
<summary>The verdicts Live shows</summary>

- <CaseVerdict example={step2} name="NeverBothGreen" />
- <CaseVerdict example={step2} name="EastCanTurnGreen" />
- <CaseVerdict example={step2} name="NorthGreenOnlyWhileEastRed" />

</details>

## Reading the Trace

A case that does not hold comes with an execution that breaks it, its trace; model checkers call it a counterexample. Open the trace of `NorthGreenOnlyWhileEastRed` and read *Steps* to its last state.

In its last state north is green while east is yellow. Going back through the steps: east turned yellow, and from then on north was free to switch, since its guard waits only while east is green; north turned green before east turned red. The guard keeps the lights from being green together, and nothing more: a light may turn green while the other is still yellow, and no case asked about it until now.

## The Fix

A light switches only while the other is red:

<LiveEmbed example={basics} show={['Intersection']} />

Run the fixed model with its three cases:

<details>
<summary>The verdicts Live shows</summary>

- <CaseVerdict example={basics} name="NeverBothGreen" />
- <CaseVerdict example={basics} name="EastCanTurnGreen" />
- <CaseVerdict example={basics} name="NorthGreenOnlyWhileEastRed" />

</details>

`EastCanTurnGreen` still matters: a guard that stopped both lights would make the two safety cases hold as well.

The whole model:

<LiveEmbed example={basics} />

## What You Can Do Now

You can compose parts with `contains` and inline calls, state a requirement as an `AG` case, and read a trace back into the model. The next lesson gives a light a minimum green time and counts with integers: [The Minimum Green Time](green-time.md).
