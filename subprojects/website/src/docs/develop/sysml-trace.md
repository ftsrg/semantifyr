---
title: SysML v2 traces
sidebar_position: 1
---

# SysML v2 traces

A witness of a SysML v2 verification case is shown as a SysML v2 model: a package `<Case>Trace` with a part definition `Run` that specializes the verified part's definition and states which states each part was in over which stretch of time, which transitions fired in which step, which items were sent, and how much time passed. Every declaration narrows the model, so the trace is a refinement of it, and a SysML v2 tool reads it next to the model.

## Inputs

`SysMLv2Frontend.verify` runs the `trace` command of the bundled SysIDE fork for every case with a witness, with three files: the case's `witness.json` and `trace.json` and the name map the translation wrote next to the OXSTS (`compiled/<variant>/<model>.names.json`). The command generates the trace, validates it against the model with SysIDE, and writes `trace.sysml` next to the witness only when it validates; otherwise the case keeps its verdict and the log carries the error, which names the place in the input it comes from (`trace.json.steps[3].traces[0].self: ...`).

The name map holds a hash of the model text, the tick, and one entry per translated element: its path in the SysML model (names joined by `::`, an unnamed element by its index `#n` among its owner's elements, an instance of a usage with multiplicity `[n]` by `[k]`), its path in the OXSTS, and for parts and subjects the class their features continue in. The generator refuses a name map whose hash does not match the model it is given, and checks the shape of all three files before it reads them.

The generator replays nothing of the library:

- The active states come from the `activeState` values of the witness, and an exhibited state's from its `performing` flag. Where the optimizer removed a flag, they come from the traced `enter` and `exit` of `State` and `ParallelState`, whose argument is the closest common state.
- A state occurrence starts where its state turns active or is entered again, and ends where it turns inactive or is left.
- The fired transitions are the traced `Transition::fire` calls, the sends the traced `SendItemAction::send` calls. A send belongs to the entry, exit or effect that holds its send action.
- A traced `passTime` of the case is a dwell of one tick. Other traced calls are not shown.

## Form

```sysmlv2
package TrafficLightABehaviorNormalGreenUnreachableViolatedTrace {
    private import Occurrences::HappensJustBefore;
    private import Time::DurationOf;
    private import SI::s;
    private import VerificationCases::VerdictKind;

    verification witness : TrafficLightABehaviorNormalGreenUnreachableViolated {
        subject :>> system : Run;
        return :>> verdict = VerdictKind::fail;
    }

    part def Run :> CrossroadSystem {
        bind init.startShot = self.startShot;

        timeslice init [1] {
            assert constraint { DurationOf(init) == 0 [s] }
            event controller.Behaviour.start [1];
        }
        succession : HappensJustBefore [1] first init then dwell1;
        timeslice dwell1 [1] {
            assert constraint { DurationOf(dwell1) == 1 [s] }
        }
        succession : HappensJustBefore [1] first dwell1 then step2;
        timeslice step2 [1] {
            assert constraint { DurationOf(step2) == 0 [s] }
            event controller.Behaviour.operating1.bothYellowToBothRed1 [1];
        }

        part controller :>> controller {
            exhibit state Behaviour :>> Behaviour [1] {
                state operating1 [1] :> Operating {
                    state bothYellow1 [1] :> BothYellow {
                        event occurrence firstDwell [1] ::> dwell1;
                        event occurrence lastDwell [1] ::> dwell1;
                    }
                    transition bothYellowToBothRed1 [1]
                        first bothYellow1
                        accept after 1[s]
                        do action {
                            action sendToggle1 [1] send new Toggle() via controlA;
                        }
                        then bothRed1;
                    state bothRed1 [1] :> BothRed;
                }
            }
        }
    }
}
```

- The verification usage `witness` redefines the subject to `Run` and binds the verdict: `fail` for a witness that breaks `mustAlways`, `pass` for one that reaches what `eventually` asks for, a negation flipping either (`VerificationCases::VerificationCase`).
- `Run` specializes the system definition (SysML 7.6.2, 8.4.7), so its values are the system lives the witness is a prefix of.
- The timeline is `init`, then `stepK` for a step in which a transition fired and `dwellK` for the tick that ends step K, each a `timeslice` with `[1]` asserting its `DurationOf` (SysML 7.9.3, 8.4.5.2; `Time::DurationOf`), consecutive ones ordered by `succession : HappensJustBefore [1]` (SysML 8.4.9.4). A reaction is a time slice of zero duration, not a snapshot: a fired transition orders the source's exit before the effect and the effect before the target's entry (SysML 8.4.14.3), which a snapshot cannot contain. `bind init.startShot = self.startShot` puts time 0 at the start of the run.
- What happened in a reaction is an `event` usage in its slice: the start of every exhibited state in `init`, every fired transition in `stepK` (SysML 7.9.5, 8.4.5.3). An event occurrence usage subsets `timeEnclosedOccurrences`, so it states "happens during" without a connection; a `connection` typed by `HappensWhile` or `HappensDuring` has an empty type, since `HappensLink` is disjoint from `Occurrence` (SysML 8.3.13.4).
- Parts and exhibited states redefine the model's under their names; a region of a parallel state is `state R :>> R [1]`; an instance of a part with multiplicity `[n]` subsets it, `part machines2 [1] :> machines`.
- An entered state is a `[1]` occurrence subsetting its state inside the occurrence of its owner, numbered within that owner (`bothRed1`, `bothRed2`). A leaf occurrence states the dwells it lasts with `firstDwell` and `lastDwell`; one enclosing the first and the last dwell encloses every dwell between them.
- A fired transition is a `[1]` transition usage in the occurrence of the state that declares it, with the source occurrence after `first`, the model's trigger and guard copied, the sends of its effect in `do action`, and the target occurrence after `then` (SysML 8.4.14.3). A named transition subsets its model transition; an unnamed one is named after its source and target and identified by its owner, ends, trigger and guard, which the translation warns about when two unnamed transitions share all of them.
- Sends of an entry or exit action are the `entry action` or `exit action` of the state occurrence that performed them, never a redefinition of the model's (SysML 8.4.14.1).
- Every declared occurrence, event, transition and succession has `[1]`: they default to `[0..*]` (SysML 7.6.3), and without a lower bound they assert nothing.
- The names the trace declares stay apart from the model's features: a clash gets the next free index.

## Time and order

A dwell lasts one tick, the duration the translation computes from the model: the largest duration in seconds, minutes, hours or days that divides every literal `after` duration of the model, one second when it has none. Two transitions fired in one step are two events of the same slice, with no order between them, and a site traced twice in one step shows once: the witness and the trace files carry neither the order of the traced calls within a step nor the time a step advanced.

## Tests

The reference pair is `subprojects/frontends/sysml/models/etc/crossroads-green.*`, Theta's `cegar-pred-cart` counterexample of `TrafficLightABehaviorNormalGreenUnreachableViolated` of `models/examples/crossroads.sysml`; it is regenerated on that configuration, since a race may return another valid witness. The fork's golden test compares the trace generated from its witness and trace files with the reference text whole. `ReferenceTraceTest` in `:sysml-wrapper` validates the reference trace with the pinned SysIDE and generates it with the bundled command, the nightly `SysMLTraceTests` checks that every witness of the SysML suites yields a trace, and the reference trace validates in the OMG pilot implementation.
