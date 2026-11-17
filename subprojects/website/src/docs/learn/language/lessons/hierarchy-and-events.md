---
title: 'Hierarchy and Events'
sidebar_position: 5
description: The fifth lesson of the Semantifyr Language. Grow the statechart library with nested states, entry actions and events, give the traffic light a standby mode that an operator controls, and find what each new rule breaks.
---

import LiveEmbed from '@site/src/components/LiveEmbed';
import LiveLink from '@site/src/components/LiveLink';
import CaseVerdict from '@site/src/components/CaseVerdict';
import { NestedStates } from '@site/src/components/LessonFigures';
import time from '@served-examples/oxsts/time';
import step1 from '@served-examples/oxsts/hierarchy-and-events-step1';
import step2 from '@served-examples/oxsts/hierarchy-and-events-step2';
import step3 from '@served-examples/oxsts/hierarchy-and-events-step3';
import step4 from '@served-examples/oxsts/hierarchy-and-events-step4';
import step5 from '@served-examples/oxsts/hierarchy-and-events-step5';
import final from '@served-examples/oxsts/hierarchy-and-events';

# Hierarchy and Events

The goal of this lesson is to grow the statechart library with the constructs of real statechart languages, nested states, entry actions and events, and to find through the requirements of the models where a new rule of the library, or of a model, goes wrong.

## Running example

At night or in maintenance, a traffic light leaves its cycle for a standby mode, in which it flashes yellow, and when it comes back it starts again from red. An operator decides when: they pause a light and resume it.

<NestedStates />

Why would a library need nested states? A light can go to standby from every color. With flat states, each color would need a transition of its own to standby. With nested states, the colors are substates of an operating state, and one transition leaves operating, whichever color is active. And why events? A sensor says how things are, for as long as they are so, but the operator's pause happens at one moment, and the light should react to it once.

The junction and the crossing keep their requirements, and the traffic light gets two more:

- A light in standby is never green.
- The lamp of a light shows green only while the light is in its green state.

The lesson starts from the final state of [Time](time.md), which can be opened in Live via <LiveLink example={time}>this link</LiveLink>.

## Nested states

A nested state contains states of its own and holds the active one, which is what a statechart does, so in the library a state becomes a statechart.

- Make `State` extend `Statechart`, and `Statechart` extend `Condition`: a statechart that runs is active, so its `isActive` says `true`.
- A state without substates has no initial state, so let the initial state of a statechart be optional, `[0..1]`, and let `init` activate it through an [optional call](../specification/foundations/expressions.md#optional-member-access-), `initial?.activate()`.
- When a state is activated, it activates its own initial substate after itself, and time passes for its substates as well as for itself.
- In the traffic light, make the three colors substates of a state `operating`, which starts at red, and add a state `standby` beside it. The light starts in operating, its color transitions reach the colors through operating, and two new transitions go from operating to standby and back, at any time for now.
- The junction and the crossing wait for the red state inside operating.
- Add the case `NorthCanStandBy`, which asks whether the north light can be in standby, and the case `NorthNeverGreenInStandby`, which requires that it is never in standby and green at the same time.

Verify all. The light can stand by, <CaseVerdict example={step1} name="NorthCanStandBy" />, but with the library of the linked state the new requirement fails: <CaseVerdict example={step1} name="NorthNeverGreenInStandby" />. Read its trace in *Steps*: the north light goes from operating to standby while it is green, and the active state of operating stays green. This state of the lesson can be opened in Live via <LiveLink example={step1}>this link</LiveLink>.

The problem is in the library. Find and fix it!

## Standby and the other light

With the fix, a light in standby is never green: <CaseVerdict example={step2} name="NorthNeverGreenInStandby" />. But two requirements that held so far now fail, <CaseVerdict example={step2} name="NorthGreenOnlyWhileEastRed" /> and <CaseVerdict example={step2} name="WalkOnlyWhileRed" />. Read the trace of the first: the north light is green, and the east light goes to standby. A light in standby flashes yellow, so the cars from the east may drive while the north light is green. This state of the lesson can be opened in Live via <LiveLink example={step2}>this link</LiveLink>.

Fixing the library made standby real, and that uncovered a flaw of the model. A light should go to standby only while the crossing traffic is stopped, just as it turns green only then. Give its transition to standby that guard.

Then every case passes again: <CaseVerdict example={step3} name="NorthGreenOnlyWhileEastRed" />, <CaseVerdict example={step3} name="WalkOnlyWhileRed" /> and <CaseVerdict example={step3} name="NorthNeverGreenInStandby" /> among them. This state of the lesson can be opened in Live via <LiveLink example={step3}>this link</LiveLink>.

## Entry actions

A light has a lamp, and the lamp is what the cars see. An entry action runs whenever a state becomes active, whichever transition enters it, which is where a state switches the lamp to its color. Behavior that differs between states goes into a subclass of `State`, since a nested block holds features only:

<LiveEmbed example={step4} show={['LightColor', 'Lamp', 'LampState']} />

A `LampState` refers to a lamp and to a color, and its entry action switches the lamp to that color.

- Give `State` an empty transition `entry`, which `activate` calls after the state has become active and before its initial substate is entered.
- Give the traffic light a lamp, and make its three colors lamp states bound to the lamp and to their color.
- Add the case `NorthGreenLampOnlyInGreen`, which requires that the north light's lamp shows green only while the light is in its green state.

The new case fails: <CaseVerdict example={step4} name="NorthGreenLampOnlyInGreen" />. Read its trace: the light turns green, then goes to standby, and its lamp still shows green, since only entry actions switch the lamp and standby has none. In standby the lamp should show yellow; make it so. Then the case passes, <CaseVerdict example={step5} name="NorthGreenLampOnlyInGreen" />. The model does not show the flashing. This state of the lesson can be opened in Live via <LiveLink example={step5}>this link</LiveLink>.

## Events

So far a light goes to standby and back at any time. Now the operator decides, with two events, pause and resume. An event belongs to a statechart, which holds at most one current event, as it holds one active state:

<LiveEmbed example={final} show={['Event']} />

An event is a condition, active while it is the current event of its statechart, so a transition takes it as a guard like any other.

- Let `Statechart` contain its events, any number of them, and hold the current one in a variable `event`, which starts at nothing.
- Let `System` give every part any one of its events, or none, in every step, before time passes.
- Give the traffic light the events `pause` and `resume`, and let the transition to standby fire only on pause, and the transition back only on resume.

Every case keeps its verdict: <CaseVerdict example={final} name="NorthCanTurnGreen" />, <CaseVerdict example={final} name="NeverBothGreen" />, <CaseVerdict example={final} name="NorthGreenOnlyWhileEastRed" />, <CaseVerdict example={final} name="PedestriansCanWalk" />, <CaseVerdict example={final} name="WalkOnlyWhileRed" />, <CaseVerdict example={final} name="NorthCanStandBy" />, <CaseVerdict example={final} name="NorthNeverGreenInStandby" /> and <CaseVerdict example={final} name="NorthGreenLampOnlyInGreen" />. In the trace of `NorthCanStandBy`, the north light's event is pause in the step it goes to standby.

The light still has the minimum green time of the previous lesson. Add the case `NorthGreenForThreeSteps` of that lesson, and decide its verdict before you verify. It fails, <CaseVerdict example={final} name="NorthGreenForThreeSteps" />, and its trace shows the operator pausing a light that has just turned green. Is that a flaw of the model, or should the requirement say what an operator may do? The final state of this lesson can be opened in Live via <LiveLink example={final}>this link</LiveLink>.

## Where the lessons end

You have built a statechart language in the Semantifyr Language: states, transitions and guards, how the parts of a system step, time, nested states, entry actions and events, each a few classes of a library, and you have seen each of its rules decide the verdicts of models that never changed. The libraries that give Gamma and SysML v2 their meaning are built from the same patterns, Gamma's with orthogonal regions, exit actions, history and priorities as well. Every construct of the language is in the [specification](../specification/index.md), and [How a Semantifyr Model Compiles](../compilation/index.mdx) shows how a model becomes what a backend checks.
