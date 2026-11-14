---
title: 'Verifying SysML v2 Models'
sidebar_position: 3
description: A tutorial that models a room with a switch and a lamp in SysML v2, verifies three requirements in Live and reads a trace.
---

import CodeBlock from '@theme/CodeBlock';
import CaseVerdict from '@site/src/components/CaseVerdict';
import LiveEmbed, { selectDeclarations } from '@site/src/components/LiveEmbed';
import room from '@served-examples/sysml/room';
import ServedExamples from '@site/src/components/ServedExamples';

# Verifying SysML v2 Models

Semantifyr verifies SysML v2 models by translating them into OXSTS on top of a semantic library that defines what SysML v2 parts, ports, items and state machines do. This tutorial models a room with a switch and a lamp, verifies three requirements in Live, and reads the trace of a requirement that does not hold. All you need is a browser; [Verifying on the Command Line](../tools/command-line.md) runs the same model on your machine.

## The Model

The room has two parts. The **switch** reacts to presses of its button and tells the lamp to toggle; the **lamp** turns on and off whenever it is told to. The parts communicate through items sent over ports. Declare the items and the port definitions:

<CodeBlock language="sysmlv2">{selectDeclarations(room.text, ['Press', 'ButtonPort', 'TogglePort'])}</CodeBlock>

`Press` and `Toggle` are item definitions without features: only their kind matters here. A `ButtonPort` receives presses, a `TogglePort` sends toggles. The `in` and `out` keywords give the direction of each item.

The switch is a part with a button, an output and a state machine:

<CodeBlock language="sysmlv2">{selectDeclarations(room.text, ['Switch'])}</CodeBlock>

The state machine starts in `Off`. The transition `turnOn` fires when a `Press` arrives on the button while the switch is `Off`; it sends a new `Toggle` through the output and moves to `On`. The transition `turnOff` does the same in the other direction. Naming the transitions is optional, but the names show up in the witness later.

The lamp has an input port that is the conjugate (`~`) of `TogglePort`, so its `toggle` item flows in:

<CodeBlock language="sysmlv2">{selectDeclarations(room.text, ['Lamp'])}</CodeBlock>

Finally, the room puts the two parts together:

<CodeBlock language="sysmlv2">{selectDeclarations(room.text, ['Room'])}</CodeBlock>

The room's own `button` port is bound to the switch's button, and a flow connects the switch's output to the lamp's input.

The semantic library executes such a model in steps. In each step, the environment may put an item on each port of the verified part; here that means someone may or may not press the button. Then every state machine with an enabled transition fires one, and every region of a parallel state does the same; an item fires at most one transition per part. At the end of the step the flows move the items sent in it to the receiving ports, which hold them for the next step only: an item that the receiver does not accept in that step is dropped. [Semantic Assumptions](assumptions.md) lists every assumption the library makes, and [What Is Supported](supported.mdx) the constructs the translator reads.

## Requirements and Verification Cases

Requirements talk to the verifier through three calculation definitions, declared at the top of the file:

<CodeBlock language="sysmlv2">{selectDeclarations(room.text, ['import', 'eventually', 'mustAlways', 'isStateActive'])}</CodeBlock>

Semantifyr recognizes them by their names: `eventually` (short name `EF`) states that some execution reaches a situation, `mustAlways` (short name `AG`) that every execution stays in one, and `isStateActive` asks whether a state is currently active. The SysML v2 example models in the repository keep these definitions in a separate `Verification.sysml` next to the models; this tutorial keeps them in the model file, so that one file is all you need.

A verification case is a `verification def` whose subject is the system under verification and whose objective is a requirement over it. The first case asks whether the lamp can ever be lit:

<CodeBlock language="sysmlv2">{selectDeclarations(room.text, ['LampLitReachable'])}</CodeBlock>

The second claims that the lamp is never lit, which is false; the repository's convention marks such expected failures with the `Violated` suffix:

<CodeBlock language="sysmlv2">{selectDeclarations(room.text, ['LampNeverLitViolated'])}</CodeBlock>

The third claims that the lamp is only ever lit while the switch is on. Before you run it, think about whether it holds:

<CodeBlock language="sysmlv2">{selectDeclarations(room.text, ['LampLitOnlyWhileSwitchOnViolated'])}</CodeBlock>

The complete model is one file. **Run** in the corner of the code block below opens it in an editor on this page:

<LiveEmbed example={room} />

## Verifying

The editor lists the three cases, a line each, under **Verify all**, which runs them all, once the SysML v2 language server in your browser has found them. The language server goes on initializing after that; a verification asked for meanwhile waits and starts by itself when it is done. Each case is verified by Live's portfolio and takes a few seconds.

<details>
<summary>The verdicts Live shows</summary>

- <CaseVerdict example={room} name="LampLitReachable" />
- <CaseVerdict example={room} name="LampNeverLitViolated" />
- <CaseVerdict example={room} name="LampLitOnlyWhileSwitchOnViolated" />

</details>

The model is yours to edit, as one file. The cases follow your edits, and a construct the translator does not support gets a diagnostic on that construct and keeps the model from being verified. **Open in Live** opens the model in Live itself, where *OXSTS of the model*, in the read-only documents menu, opens the translation of the current file, an OXSTS model that instantiates the classes of the SysML v2 semantic library.

## Reading the Witness

A case with a witness says **Trace** at the end of its line, and a click on the line opens its trace below it, in four views:

- *SysML trace*: the witness as a SysML v2 model, a package `<case>Trace` whose part definition `Run` specializes the verified part definition and declares the states, transitions and sends of this execution in time order ([SysML v2 traces](../reference/artifacts/sysml-trace.mdx)).
- *OXSTS steps*: the values of the model's state in every step of the witness, starting from the initial state that initialization produces. State names are paths from the verified instance, `root`, shortened to their last segments, with the whole path on hover.
- *Calls*: per step, the traced calls of the semantic library: the transitions that fired, the states entered and left, and the send actions that ran.
- *OXSTS witness*: the witness as an OXSTS model.

After **Verify all** the trace of the first failed case opens by itself. A failed `mustAlways` requirement has a trace. Open the trace of `LampLitOnlyWhileSwitchOnViolated` and read it in *Calls* and *OXSTS steps*. The shortest trace has two steps:

1. The button is pressed: `root.system.switch.Behaviour.turnOn` fires and the switch's output sends a `Toggle`. Afterwards the switch is `On`, the lamp is still `Dark`, and the toggle waits on `root.system.lamp.input`.
2. The button is pressed again: `turnOff` fires and sends a second `Toggle`, while the lamp accepts the first one and `root.system.lamp.Behaviour.lightUp` fires. Afterwards the switch is `Off` and the lamp is `Lit`.

The requirement fails because the parts communicate asynchronously: the lamp follows the switch one step later. Whether that is a defect of the design or of the requirement is for the engineer to decide.

The *SysML trace* view tells the same story in SysML v2: the package `LampLitOnlyWhileSwitchOnViolatedTrace` holds a verification usage of the case with the verdict `fail` and a part definition `Run` that specializes `Room`, whose `switch` and `lamp` redefine the room's parts. Their exhibited states hold the occurred states and the fired transitions, each with its trigger and the toggles its effect sent, and every leaf state names the first and last tick it lasted. Time slices order the run: `init`, a zero-duration `stepK` for each step in which a transition fired, and a `dwellK` for the tick that ends step K. Since the trace refines `Room`, any SysML v2 tool reads it next to the model.

The verifier finds a witness for `LampLitReachable` the same way: an execution in which the lamp lights up. A passing `mustAlways` requirement or a failing `eventually` requirement has no witness, since no single execution proves it.

## The Model in Live

The room opens in Live by its own address:

<ServedExamples />
