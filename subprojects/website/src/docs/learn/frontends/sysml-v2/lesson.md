---
title: 'Lesson: A Switch and a Lamp'
sidebar_position: 2
description: Model a room with a switch and a lamp in SysML v2, verify three requirements, and read the trace of one that does not hold.
---

import LiveEmbed from '@site/src/components/LiveEmbed';
import room from '@served-examples/sysml/room';

# Lesson: A Switch and a Lamp

This lesson models a room with a switch and a lamp in SysML v2, verifies three requirements, and reads the trace of a requirement that does not hold.

## The Model

The room has two parts. The **switch** reacts to presses of its button and tells the lamp to toggle; the **lamp** turns on and off whenever it is told to. The parts communicate through items sent over ports. Declare the items and the port definitions:

<LiveEmbed example={room} show={['Press', 'ButtonPort', 'TogglePort']} />

`Press` and `Toggle` are item definitions without features: only their kind matters here. A `ButtonPort` receives presses, a `TogglePort` sends toggles. The `in` and `out` keywords give the direction of each item.

The switch is a part with a button, an output and a state machine:

<LiveEmbed example={room} show={['Switch']} />

The state machine starts in `Off`. The transition `turnOn` fires when a `Press` arrives on the button while the switch is `Off`; it sends a new `Toggle` through the output and moves to `On`. The transition `turnOff` does the same in the other direction. Naming the transitions is optional, but the names show up in the witness later.

The lamp has an input port that is the conjugate (`~`) of `TogglePort`, so its `toggle` item flows in:

<LiveEmbed example={room} show={['Lamp']} />

Finally, the room puts the two parts together:

<LiveEmbed example={room} show={['Room']} />

The room's own `button` port is bound to the switch's button, and a flow connects the switch's output to the lamp's input.

The model runs in steps. In each step someone may press the button, every state machine fires one enabled transition, and the items sent move along the flows, arriving in the next step. [Semantic Decisions](semantic-decisions.mdx) states the full semantics.

## Requirements and Verification Cases

Requirements talk to the verifier through three calculation definitions, declared at the top of the file:

<LiveEmbed example={room} show={['import', 'eventually', 'mustAlways', 'isStateActive']} />

Semantifyr recognizes them by their names, as [Writing a Verification Case](verifying.md#writing-a-verification-case) says.

A verification case is a `verification def` whose subject is the system under verification and whose objective is a requirement over it. The first case asks whether the lamp can ever be lit:

<LiveEmbed example={room} show={['LampLitReachable']} />

The second claims that the lamp is never lit, which is false:

<LiveEmbed example={room} show={['LampNeverLitViolated']} />

The third claims that the lamp is only ever lit while the switch is on. Before you run it, think about whether it holds:

<LiveEmbed example={room} show={['LampLitOnlyWhileSwitchOnViolated']} />

## Verifying

Press **Verify all** in any of the editors above; each verifies the whole model. Each case is verified in a few seconds.

To verify it on your machine, save the model as `room.sysml` and run `semantifyr-sysml verify room.sysml`.

## Reading the Trace

A case has a trace when a single execution proves its verdict: a failed `mustAlways` or a passed `eventually` requirement. Open the trace of `LampLitOnlyWhileSwitchOnViolated`; it has two steps:

1. The button is pressed: `root.system.switch.Behaviour.turnOn` fires and the switch's output sends a `Toggle`. Afterwards the switch is `On`, the lamp is still `Dark`, and the toggle waits on `root.system.lamp.input`.
2. The button is pressed again: `turnOff` fires and sends a second `Toggle`, while the lamp accepts the first one and `root.system.lamp.Behaviour.lightUp` fires. Afterwards the switch is `Off` and the lamp is `Lit`.

The requirement fails because the parts communicate asynchronously: the lamp follows the switch one step later. Whether that is a defect of the design or of the requirement is for the engineer to decide.

The *SysML trace* view shows the same run as a SysML v2 model that refines `Room` ([SysML v2 traces](traces.mdx)); on your machine it is `trace.sysml`, next to the case's `report.json`.
