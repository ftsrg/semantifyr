---
title: 'Semantic Variants'
sidebar_position: 3
description: The third lesson of the Semantifyr Language. Move into the statechart library the rule of how the parts of a system step, change it as statechart languages differ on it, and see the same junction meet or break its requirements.
---

import LiveLink from '@site/src/components/LiveLink';
import CaseVerdict from '@site/src/components/CaseVerdict';
import { CompositionRules, TraceTimeline } from '@site/src/components/LessonFigures';
import libraries from '@lesson-stages/semantic-libraries';
import step1 from '@lesson-stages/semantic-variants-step1';
import step2 from '@lesson-stages/semantic-variants-step2';
import step3 from '@lesson-stages/semantic-variants-step3';
import step4 from '@lesson-stages/semantic-variants-step4';
import final from '@lesson-stages/semantic-variants';

# Semantic Variants

The goal of this lesson is to change a rule of the statechart library, and to see that under the new rule the same models meet or break their requirements.

## Running example

The running example is the junction of [Semantic Libraries](semantic-libraries.md), with its crosswalk and its requirements: the two traffic lights are never both green, a light is green only while the other one is red, pedestrians walk only while the east light is red, a waiting car can get green and pedestrians can walk. We continue from where that lesson ended, <LiveLink example={libraries}>its final state</LiveLink>.

Why would anyone change a rule of the library? Because the languages built on statecharts do not agree on their rules, and the same diagram then behaves differently from tool to tool. One rule on which they differ is how the parts of a system step. In some, one part moves in a step while the others wait. In some, every part moves in every step, one after the other. In others, every part moves at once, and each reacts to what it saw before the step. Gamma, for one, has all three, as its asynchronous, cascade and synchronous composition. Whoever defines a language has to know what such a rule does to the models written in it. With the rule in a library, finding out takes one edit and a verification run.

The figure shows one step of the junction under each of the three rules, from both lights red with a car waiting at each.

<CompositionRules />

## Composition in the library

The junction still writes how it steps: its `init` starts its lights, and its main transition lets the environment set the sensors and the button and then steps one of the lights. Any other system written on the library would write the same lines, so that is a rule of the language as well. Let us move it into the library.

- The library has a class `System`, which contains any number of sensors and any number of parts, the parts being statecharts.
- A system starts every part, and in every step the environment sets every sensor and then one of the parts steps.
- The junction is a system and says only which sensors and which parts it has: its own `init` and main transition go.

Verify all. The junction does what it did before, so every case keeps its verdict: <CaseVerdict example={step1} name="NorthCanTurnGreen" />, <CaseVerdict example={step1} name="NeverBothGreen" />, <CaseVerdict example={step1} name="NorthGreenOnlyWhileEastRed" />, <CaseVerdict example={step1} name="PedestriansCanWalk" /> and <CaseVerdict example={step1} name="WalkOnlyWhileEastRed" />. The junction is data alone now, as in <LiveLink example={step1}>this state of the lesson</LiveLink>.

## Cascade composition

Let us change the rule so that every part steps in every step, one after the other, in the order the parts are declared, as a cascade composition does. Change only `System`, and before you verify, decide the verdict of each case.

Every case keeps its verdict again: <CaseVerdict example={step2} name="NorthCanTurnGreen" />, <CaseVerdict example={step2} name="NeverBothGreen" />, <CaseVerdict example={step2} name="NorthGreenOnlyWhileEastRed" />, <CaseVerdict example={step2} name="PedestriansCanWalk" /> and <CaseVerdict example={step2} name="WalkOnlyWhileEastRed" />. Both lights may now move in the same step: why are they still never green together? Compare your rule with <LiveLink example={step2}>the cascade rule of this state</LiveLink>.

## Synchronous composition

In a synchronous composition, every part reacts to the same moment: each sees the state before the step, and all of them move at once. If a part fired its transition as soon as it chose it, the parts after it would already see its new state. So a step has two halves: first every part chooses the transition it fires, or none, and then all of them move. We get there in three changes, and verify after each.

First, split what a transition does into two transitions of its own: `assumeEnabled`, which can be taken only when the transition can fire, and `fire`, which activates the target. `@Trace` marks `fire`. The main transition of `Transition` calls both, so nothing changes yet: verify all, and every case keeps its verdict. Compare with <LiveLink example={step3}>this state</LiveLink>.

Second, let a statechart choose before it moves. It remembers its choice in a variable that holds one of its transitions or nothing, as `activeState` holds one of its states. Give `Statechart` two transitions, `choose`, which chooses one of the transitions that can fire, or none, and `move`, which fires the chosen one. Nothing calls them yet, so every case keeps its verdict again. Compare with <LiveLink example={step4}>this state</LiveLink>.

Third, switch the system: after the sensors, every part chooses, and then every part moves. Before you verify, decide the verdict of each case.

This time three of them fail: <CaseVerdict example={final} name="NeverBothGreen" />, <CaseVerdict example={final} name="NorthGreenOnlyWhileEastRed" /> and <CaseVerdict example={final} name="WalkOnlyWhileEastRed" />. The other two still pass, <CaseVerdict example={final} name="NorthCanTurnGreen" /> and <CaseVerdict example={final} name="PedestriansCanWalk" />. In the trace of `NeverBothGreen`, both lights turn green in the same step:

<TraceTimeline rows={[{ name: 'north', lamps: ['Red', 'Green'] }, { name: 'east', lamps: ['Red', 'Green'] }, { name: 'north car', flags: [false, true] }, { name: 'east car', flags: [false, true] }]} broken="NeverBothGreen" />

Notice that we did not change any of the primary classes, only their behaviors. The lights, the junction and their cases are as they were. The library's classes kept their features too, except for the one new variable that holds a statechart's choice. In the first lesson, we fixed the guard so that a light turns green only while the other one is red. That guard is enough only as long as the two lights never decide at the same moment. Under the new rule they do: each sees the other one red, and both turn green. <LiveLink example={final}>The final state of this lesson</LiveLink> has the library with the synchronous rule.

Why do the two cases that ask whether something can happen still pass?

What a model means is decided by the library it is written on. This is how Semantifyr gives SysML v2 and Gamma their meaning, each with a library of its own. The next lesson, [Extending the Library](extending-the-library.md), goes back to the first rule, one part in a step, and gives the library a new concept.
