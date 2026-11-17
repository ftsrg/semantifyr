---
title: 'Semantic Variants'
sidebar_position: 3
description: The third lesson of the Semantifyr Language. Move into the statechart library the rule of how the parts of a system step, change it as statechart languages differ on it, and see the same junction meet or break its requirements.
---

import LiveLink from '@site/src/components/LiveLink';
import CaseVerdict from '@site/src/components/CaseVerdict';
import { CompositionRules, TraceTimeline } from '@site/src/components/LessonFigures';
import libraries from '@served-examples/oxsts/semantic-libraries';
import step1 from '@served-examples/oxsts/semantic-variants-step1';
import step2 from '@served-examples/oxsts/semantic-variants-step2';
import final from '@served-examples/oxsts/semantic-variants';

# Semantic Variants

The goal of this lesson is to change a rule of the statechart library, and to see that under the new rule the same models meet or break their requirements.

## Running example

The running example is the junction and the crossing of [Semantic Libraries](semantic-libraries.md), with their requirements: the lights of the junction are never both green, a light is green only while the other one is red, pedestrians walk only while the traffic light is red, and a waiting car can get green and pedestrians can walk. The lesson starts from the final state of that lesson, which can be opened in Live via <LiveLink example={libraries}>this link</LiveLink>.

Why would anyone change a rule of the library? Because the languages built on statecharts do not agree on their rules, and the same diagram then behaves differently from tool to tool. One rule on which they differ is how the parts of a system step. In some, one part moves in a step while the others wait. In some, every part moves in every step, one after the other. In others, every part moves at once, and each reacts to what it saw before the step. Gamma, for one, has all three, as its asynchronous, cascade and synchronous composition. Whoever defines a language has to know what such a rule does to the models written in it, and with the rule in a library, finding out is one edit and a verification run.

The figure shows one step of the junction under each of the three rules, from both lights red with a car waiting at each.

<CompositionRules />

## Stepping in the library

The junction and the crossing still write how they step: their `init` starts their lights, and their main transitions let the environment set the sensors and then step one of the lights, the same lines in both. That is a rule of the language as well, so let us move it into the library.

- Add a class `System` to the library, which contains any number of sensors and any number of parts, the parts being statecharts.
- Its `init` starts every part.
- Its main transition lets the environment set every sensor, and then steps one of its parts.
- Make `Junction` and `Crossing` systems: their sensors and their lights subset the sensors and the parts of `System`, and their own `init` and main transitions go.

Verify all. Nothing changed in what the junction and the crossing do, so every case keeps its verdict: <CaseVerdict example={step1} name="NorthCanTurnGreen" />, <CaseVerdict example={step1} name="NeverBothGreen" />, <CaseVerdict example={step1} name="NorthGreenOnlyWhileEastRed" />, <CaseVerdict example={step1} name="PedestriansCanWalk" /> and <CaseVerdict example={step1} name="WalkOnlyWhileRed" />. The junction and the crossing are now data alone. This state of the lesson can be opened in Live via <LiveLink example={step1}>this link</LiveLink>.

## One after the other

Let us change the rule so that every part steps in every step, one after the other, in the order the parts are declared, as a cascade composition does. Change only the main transition of `System`, and before you verify, decide the verdict of each case.

Every case keeps its verdict again: <CaseVerdict example={step2} name="NorthCanTurnGreen" />, <CaseVerdict example={step2} name="NeverBothGreen" />, <CaseVerdict example={step2} name="NorthGreenOnlyWhileEastRed" />, <CaseVerdict example={step2} name="PedestriansCanWalk" /> and <CaseVerdict example={step2} name="WalkOnlyWhileRed" />. Both lights of the junction may now move in the same step: why are they still never green together? This state of the lesson can be opened in Live via <LiveLink example={step2}>this link</LiveLink>.

## All at once

In a synchronous composition, every part reacts to the same moment: each sees the state before the step, and all of them move at once. A part cannot fire a transition the moment it chooses it, since the parts after it would then see its new state. So a step comes in two halves: every part first chooses the transition it fires, or none, and only then do all of them move. Let us change the rule once more.

- Split the main transition of `Transition` in two: `assumeEnabled`, which assumes that the transition can fire, and `fire`, which activates its target and is the one that `@Trace` marks. The main transition calls both.
- Give `Statechart` a variable `chosen`, which holds one of its transitions or nothing, and two transitions: `choose`, which chooses one of the transitions that can fire, or none, and `move`, which fires the chosen one.
- Change the main transition of `System`: after the sensors, every part chooses, and then every part moves.

Before you verify, decide the verdict of each case. This time three of them fail: <CaseVerdict example={final} name="NeverBothGreen" />, <CaseVerdict example={final} name="NorthGreenOnlyWhileEastRed" /> and <CaseVerdict example={final} name="WalkOnlyWhileRed" />. The other two still pass, <CaseVerdict example={final} name="NorthCanTurnGreen" /> and <CaseVerdict example={final} name="PedestriansCanWalk" />. A trace of `NeverBothGreen` looks like this:

<TraceTimeline rows={[{ name: 'north', lamps: ['Red', 'Green'] }, { name: 'east', lamps: ['Red', 'Green'] }, { name: 'north car', flags: [false, true] }, { name: 'east car', flags: [false, true] }]} broken="NeverBothGreen" />

Notice that we did not change any of the primary classes, only their behaviors. The lights, the junction, the crossing and their cases are as they were, and the library's classes kept their features, but for the one variable that holds a part's choice. The guard that the first lesson found, that a light turns green only while the other one is red, is enough only as long as the two lights never decide at the same moment. Under the new rule they do, and each saw the other one red. This state of the lesson can be opened in Live via <LiveLink example={final}>this link</LiveLink>.

Why do the two cases that ask whether something can happen still pass?

What a model means is decided by the library it is written on. This is how Semantifyr gives SysML v2 and Gamma their meaning, each with a library of its own. The next lesson, [Time](time.md), goes back to the first rule, one part in a step, and gives the library a sense of time.
