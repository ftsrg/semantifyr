---
title: Gamma traces
sidebar_position: 2
---

# Gamma traces

A witness of a Gamma verification case is written as a Gamma execution trace, `trace.get`, next to the back-annotated `witness.oxsts`. The trace is a `.get` file of its own in Gamma's execution trace syntax, with two extensions: its header names a verification case, and it asserts the transitions that fired. Every name in it is a cross-reference into the model, so the Gamma language checks a trace like any other Gamma file.

The reference pair is `subprojects/frontends/gamma/models/etc/crossroads-ACanBeGreen.*`, a witness of the case `ACanBeGreen` of `models/examples/Crossroads.gamma`: `trace.json` and `witness.json` as Theta's witness produced them, and the hand-written `trace.get` they generate.

## Form

```gamma
import Crossroads

trace ACanBeGreenTrace of ACanBeGreen

step {
    act {
        reset
    }
    assert {
        raise trafficLightA.lightControl.yellow;
        raise trafficLightB.lightControl.yellow;
        state controller.Main.Operating;
        state controller.OperatingRegion.BothYellow;
        state trafficLightA.Main.Normal;
        state trafficLightA.NormalRegion.Yellow;
        state trafficLightB.Main.Normal;
        state trafficLightB.NormalRegion.Yellow;
    }
}
```

- `import` names the case's package by its qualified name, as a Gamma model imports a package.
- `trace <name> of <case>` names the traced verification case; the traced component is the case's component, in whose scope every name of the trace resolves, as in the case's property.
- Instances are named from the traced component down, `controller` here, nested ones as `outer->inner`; an element of the traced component itself is named without an instance, `state Main.Idle`.
- The first step is initialization: the `reset` act, the outputs the entry actions raised and the state reached.

The last step of the pair:

```gamma
step {
    act {
        raise control.toggle
        elapse 1000
        schedule component
    }
    assert {
        transition trafficLightA from Red to Green;
        raise trafficLightA.lightControl.green;
        state controller.Main.Operating;
        state controller.OperatingRegion.TrafficOnA;
        state trafficLightA.Main.Normal;
        state trafficLightA.NormalRegion.Green;
        state trafficLightB.Main.Normal;
        state trafficLightB.NormalRegion.Red;
    }
}
```

Acts:

- `raise <port>.<event>`: the environment raises an input event of a port of the traced component; an act names no instance.
- `elapse <milliseconds>`: time advances before the step.
- `schedule component`: the component takes one step.

Asserts, each closed by `;`:

- `state <instance>.<region>.<state>`: a state is active.
- `variable <instance>.<variable> = <value>`: a statechart variable has a value.
- `raise <instance>.<port>.<event>`: an instance raised an output event of its port, `raise <port>.<event>` the traced component itself.
- `transition <instance> from <state> to <state>`: a transition fired, followed by `when <trigger>` when another transition shares its source and target; a transition with an `@(id)` is named `transition <instance>.<id>`.

The validator checks that an act raises an input event and an assert an output event of its port, that a fired transition exists and is unambiguous, and that a value matches its variable's type; scoping checks that every name belongs to the instance it follows.

## Generation

The Gamma semantic library marks four transitions with `@Trace`: `Transition::fire`, `Port::raiseInputEvent(event)`, `Port::setOutputEvent(event)` and `GammaVerificationCase::passTime`. A verify run writes, step by step, the traced calls to `trace.json` and the state to `witness.json`, both named by their path from the root instance. `GammaTraceGenerator` resolves each path through the name map the Gamma compiler filled when it emitted the OXSTS model in the same verify run, so it never decodes OXSTS names, and maps:

- a fired `Transition::fire` to a `transition` assert;
- `Port::raiseInputEvent` to a `raise` act on the port of the traced component the inner port is bound to, and `Port::setOutputEvent` to a `raise` assert on the port of the instance that raised it, as the called transition says;
- `passTime` at the root to `elapse 1000` in the same step, before `schedule`;
- the active state of every region and the value of every statechart variable in the witness to `state` and `variable` asserts.

Input events are traced because the library resets them within the step, so no state between steps shows them. Output events are traced because the optimizer removes outputs that nothing reads. The Gamma language's serializer and formatter write the trace.

Not in the trace: negations of the output events not raised, timers and port variables; the values of event parameters, since a traced call records no runtime argument, so a raise of an event with parameters is written without its arguments; the values of record-typed variables; the last state a shallow history node holds, which is not an active state; and the firing of a transition into a history node without an `@(id)`, which the `from`/`to` form cannot name, so only the states it entered show it. The time step is fixed at one second.

## Validation

- `GammaReferenceTraceTest` generates the trace from the pair's JSON files and checks that it serializes to the hand-written `trace.get`.
- `GammaTraceLanguageTest` covers the validator's rules.
- `GammaTraceTests` (integration) verifies every `EF` case of the three examples on Theta, nuXmv, Spin and Uppaal, and checks each generated trace: it parses, links and validates against the model, has one step per witness step, asserts the witness's active states and variable values, and every fired transition leaves a state active before its step and enters one active after it, which checks the traced calls against the witness state.
- `GammaTraceLanguageServerTest` opens a trace next to its model in the language server, without errors, and checks that the trace relinks when the model changes.
- `GammaWitnessTests` (nightly) re-verifies every engine's witness on all four engines.

The VS Code extension's language server opens a `.get` trace like any Gamma file, and the live editor's Gamma language shows the trace in the *Gamma trace* view of the trace under the cases.

## Witness validation

The language server's `gamma.witness.validate` command re-verifies the witness of a case. It takes the case as `gamma.case.verify` does, a `uri`, a `range` and an optional `portfolio`, loads the `witness.oxsts` the case's last verify run wrote with the compiled model next to it, and verifies a copy of the back-annotated witness on the requested portfolio, or on the one the server settings name, writing that run's artifacts to `validation/` in the case's directory, so the verify's artifacts and the open witness stay as they are. The result is `valid` when the witness verifies `passed`, `invalid` when it verifies `failed`, `inconclusive` when the run is inconclusive, times out or is not supported, and `errored` when it errors, the case has no witness yet or the witness does not load; it carries the verdict, the portfolio and the backend's message. The command finds the witness in the artifacts directory, `<workspace>/.artifacts/` under `workspace` artifacts and under `temporary` the one directory a language server keeps until it exits, so there it finds none after a restart. The live editor's Gamma language sends it for every case with a witness when auto-validate is on, and from **Validate** or **Validate again** in the case's row, and marks the trace *validated* when the witness verifies. The VS Code extension sends it after every verify with a witness when `semantifyr.verification.autoValidate` is on, adding the result to the test message, and from **Validate Witness** on the failure message or the case's context menu, reporting it in a notification; both add it to the case's trace document.
