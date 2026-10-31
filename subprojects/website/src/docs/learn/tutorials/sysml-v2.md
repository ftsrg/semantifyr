---
title: 'SysML v2 on the Command Line'
sidebar_position: 2
---

import CodeBlock from '@theme/CodeBlock';
import { selectDeclarations } from '@site/src/components/LiveEmbed';
import room from '@sysml-tutorial-models/room.sysml';

# SysML v2 on the Command Line

Semantifyr verifies SysML v2 models by translating them into OXSTS on top of a semantic library that defines what SysML v2 parts, ports, items and state machines do. This tutorial models a room with a switch and a lamp, verifies three requirements from the command line, and reads the counterexample of a requirement that does not hold. The live editor has no SysML v2 flavor yet, so this tutorial runs locally.

## Requirements

- Git, and a Java 25 runtime found through `JAVA_HOME` or `PATH`.
- Node.js 22 or newer on `PATH`: the SysML v2 front end, a fork of SysIDE, runs on Node.js.
- A local build of Semantifyr. The release zip carries the `semantifyr` command and the SysML v2 semantic library, but not the translator from SysML v2 to OXSTS, so the translator is built from source in any case.

Build the command-line interface and the translator (the first build downloads its dependencies and takes several minutes):

```bash
git clone https://github.com/ftsrg/semantifyr.git
cd semantifyr
./gradlew :semantifyr-cli:installDist :sysml-wrapper:bundleCli
export SEMANTIFYR_HOME="$PWD/subprojects/semantifyr-cli/build/install/semantifyr"
export SYSIDE="$PWD/subprojects/frontends/sysml/sysml-wrapper/build/cli-bundle"
```

`SEMANTIFYR_HOME` now holds the same layout as the release zip: the launcher in `bin/`, Theta in `theta-xsts-cli/` and the semantic libraries in `libraries/`. If you use the [release zip](../getting-started.md#option-d-install-the-command-line-interface-for-scripts-and-ci) instead, point `SEMANTIFYR_HOME` at the unzipped `semantifyr-<version>` directory. `SYSIDE` holds the translator `cli.cjs` and the SysML v2 standard library it resolves against. The commands on this page are written for Linux and macOS shells; on Windows, use `bin\semantifyr.bat` and put `theta-xsts-cli` on `PATH` yourself.

## The Model

The room has two parts. The **switch** reacts to presses of its button and tells the lamp to toggle; the **lamp** turns on and off whenever it is told to. The parts communicate through items sent over ports. Declare the items and the port definitions:

<CodeBlock language="sysmlv2">{selectDeclarations(room, ['Press', 'ButtonPort', 'TogglePort'])}</CodeBlock>

`Press` and `Toggle` are item definitions without features: only their kind matters here. A `ButtonPort` receives presses, a `TogglePort` sends toggles. The `in` and `out` keywords give the direction of each item.

The switch is a part with a button, an output and a state machine:

<CodeBlock language="sysmlv2">{selectDeclarations(room, ['Switch'])}</CodeBlock>

The state machine starts in `Off`. The transition `turnOn` fires when a `Press` arrives on the button while the switch is `Off`; it sends a new `Toggle` through the output and moves to `On`. The transition `turnOff` does the same in the other direction. Naming the transitions is optional, but the names show up in the witness later.

The lamp has an input port that is the conjugate (`~`) of `TogglePort`, so its `toggle` item flows in:

<CodeBlock language="sysmlv2">{selectDeclarations(room, ['Lamp'])}</CodeBlock>

Finally, the room puts the two parts together:

<CodeBlock language="sysmlv2">{selectDeclarations(room, ['Room'])}</CodeBlock>

The room's own `button` port is bound to the switch's button, and a flow connects the switch's output to the lamp's input.

The semantic library executes such a model in steps. In each step, the environment may put an item on each input port of the verified part; here that means someone may or may not press the button. Each state machine then fires at most one enabled transition, and a flow delivers the items sent in a step to the receiving port in the next step. An item that the receiver does not accept in that step is dropped.

## Requirements and Verification Cases

Requirements talk to the verifier through three calculation definitions, declared at the top of the file:

<CodeBlock language="sysmlv2">{selectDeclarations(room, ['import', 'eventually', 'mustAlways', 'isStateActive'])}</CodeBlock>

Semantifyr recognizes them by their names: `eventually` (short name `EF`) states that some execution reaches a situation, `mustAlways` (short name `AG`) that every execution stays in one, and `isStateActive` asks whether a state is currently active. The SysML v2 example models in the repository keep these definitions in a separate `Verification.sysml` next to the models; this tutorial keeps them in the model file, so that one file is all you need.

A verification case is a `verification def` whose subject is the system under verification and whose objective is a requirement over it. The first case asks whether the lamp can ever be lit:

<CodeBlock language="sysmlv2">{selectDeclarations(room, ['LampLitReachable'])}</CodeBlock>

The second claims that the lamp is never lit, which is false; the repository's convention marks such expected failures with the `Violated` suffix:

<CodeBlock language="sysmlv2">{selectDeclarations(room, ['LampNeverLitViolated'])}</CodeBlock>

The third claims that the lamp is only ever lit while the switch is on. Before you run it, think about whether it holds:

<CodeBlock language="sysmlv2">{selectDeclarations(room, ['LampLitOnlyWhileSwitchOnViolated'])}</CodeBlock>

Create an empty directory and save the complete model in it as `room.sysml`. The translator reads every `.sysml` file in the model's directory, so keep other models out of it.

<CodeBlock language="sysmlv2" title="room.sysml">{room}</CodeBlock>

## Verifying

In the directory of `room.sysml`, translate the model to OXSTS, then verify it:

```bash
node "$SYSIDE/cli.cjs" compile room.sysml "$SYSIDE/sysml.library" -o out/room.oxsts
"$SEMANTIFYR_HOME/bin/semantifyr" verify out --library "$SEMANTIFYR_HOME/libraries/sysmlv2" --artifact-directory artifacts
```

The translation takes about a minute and writes `out/room.oxsts`, an OXSTS model that instantiates the classes of the SysML v2 semantic library. The `verify` command loads the library given with `--library`, finds the three verification cases in `out` and verifies each with the default `theta-full` portfolio.

For each case, the command prints its name, `PASSED` or `FAILED` with the time it took, and the path of its `report.json`, and it exits with status 5 when a case fails (the [exit codes](../cli/index.mdx#exit-codes)). Expect `LampLitReachable` to pass and both `Violated` cases to fail. The case names start with `semantifyr::sysml::model`, the package the translator gives every model.

## Reading the Witness

`--artifact-directory artifacts` keeps the artifacts of every case in a directory named after the case. Next to the backend's working files, the compiler's intermediate models (`pipeline/`) and the run's measurements (`report.json`), a case with a witness has three files:

- `witness.json`: the values of the model's state in every step of the witness: `initialStep` before initialization, `initializedStep` after it, then one entry per step. State names are paths from the verified instance, `root`.
- `trace.json`: per step, the traced calls of the semantic library: the transitions that fired and the items that were sent.
- `witness.oxsts`: the witness as an OXSTS model that replays exactly this execution.

A failed `mustAlways` requirement has a counterexample. Open `trace.json` and `witness.json` in `artifacts/semantifyr.sysml.model.LampLitOnlyWhileSwitchOnViolated/`. The shortest counterexample has two steps:

1. The button is pressed: `root.system.switch.Behaviour.turnOn` fires and the switch's output sends a `Toggle`. Afterwards the switch is `On`, the lamp is still `Dark`, and the toggle waits on `root.system.lamp.input`.
2. The button is pressed again: `turnOff` fires and sends a second `Toggle`, while the lamp accepts the first one and `root.system.lamp.Behaviour.lightUp` fires. Afterwards the switch is `Off` and the lamp is `Lit`.

The requirement fails because the parts communicate asynchronously: the lamp follows the switch one step later. Whether that is a defect of the design or of the requirement is for the engineer to decide.

The verifier finds a witness for `LampLitReachable` the same way: an execution in which the lamp lights up. A passing `mustAlways` requirement or a failing `eventually` requirement has no witness, since no single execution proves it.

:::info[Planned]
A SysML v2 trace, `trace.sysml`, that expresses the witness as a SysML v2 model of the execution. Its generator exists in the SysIDE fork but is not part of the verification flow yet.
:::

You can also browse a witness in the standalone [live editor](https://live.semantifyr.org): pick *Semantifyr with SysML v2 library* in its model picker, replace the example with the content of `out/room.oxsts`, and verify the cases there. The *State* view of the *Witness* tab shows the same values as `witness.json`.
