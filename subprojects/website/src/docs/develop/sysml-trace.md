---
title: SysML v2 traces
sidebar_position: 1
---

# SysML v2 traces

A counterexample to a SysML v2 verification case is shown as a SysML v2 model: a part definition that specializes the system definition and declares the states, transitions and sends that occurred, ordered in time. The trace refines the system model, so SysML v2 tools read and check it like any other model. The JSON input of the trace generator is still under design, and the generator in the SysIDE fork is not yet wired into the verify flow.

The reference pair is `subprojects/frontends/sysml/models/etc/crossroads-green.*`, a counterexample to `TrafficLightABehaviorNormalGreenUnreachableViolated` of `models/examples/crossroads.sysml`. `ReferenceTraceTest` in `:sysml-wrapper` validates the SysML trace with the pinned SysIDE, and it also validates without errors or warnings in the OMG pilot implementation. Citations below refer to the 2024-12 release, KerML 1.0 Beta 2.4 and SysML 2.0 Beta 2.4.

## Refinement

```sysml
part def TrafficLightABehaviorNormalGreenUnreachableViolatedTrace :> CrossroadSystem {
    part controller :>> controller {
        exhibit state Behaviour[1] :>> Behaviour {
```

- The trace definition specializes the system definition, so every trace instance is a system instance (KerML 7.3.3.3 Subclassification, SysML 7.6.2 Definitions).
- Parts are named redefinitions of the system's parts: a redefining feature has the values of the redefined one (KerML 7.3.4.5 Redefinition). The name is the effective name an unnamed redefinition would get (SysML 7.6.5 Effective Names); SysIDE resolves feature chains only through the named form.
- Exhibited states are redefined the same way; an exhibit state usage is referential and its part is the performer (SysML 7.18.4 Exhibit State Usages, 8.4.14.4).

## Multiplicity

Every occurrence the trace declares, and every connector, has multiplicity `[1]`. State, action, snapshot, time slice and connection usages default to `[0..*]` (SysML 7.6.3 Usages gives `[1..1]` only to attribute, item, part and port usages), and a connector without a lower bound holds with no link (KerML 8.4.4.6.1 Connectors). Without `[1]` the trace would assert nothing.

## States and transitions

```sysml
state operating1[1] :> Operating {
    state bothYellow1[1] :> BothYellow;
    action toBothRed1[1] :> stateTransitions {
        action sendToggleA1[1] send new Toggle() via controlA;
        action sendToggleB1[1] send new Toggle() via controlB;
    }
    state bothRed1[1] :> BothRed;
```

- An occurred state subsets the original inside the occurrence of its owning state. A state usage in a non-parallel state is one of its `exclusiveStates`, which do not overlap in time (SysML 8.4.14.2 State Usages, 9.2.11.2.1 StateAction). Only leaf states are declared; substates happen during their state, so the nesting implies the composite ones.
- An occurred transition is an action subsetting `stateTransitions` of its owning state, the feature every transition of that state specializes (SysML 8.4.14.3 Transition Usages, 9.2.11.2.3 StateTransitionAction). It subsets the original transition instead when the original is named. Unnamed transitions between the same two states cannot be told apart in the trace; the source and target follow from the states around it.
- A send is a send action nested in the transition occurrence of the firing that performed it, so it happens during the transition (SysML 7.17.7 Send Action Usages, 8.4.13.5). Sends during initialization are nested in an `action startup` of the sending part, since a part body cannot hold a send node (SysML 8.2.2.17).

## Time

```sysml
snapshot step0[1];
timeslice dwell1[1] { assert constraint { DurationOf(dwell1) == 1 [s] } }
snapshot step2[1];

connection : HappensWhile[1] connect step0 to self.startShot;
succession : HappensJustBefore[1] first step0 then dwell1;
succession : HappensJustBefore[1] first dwell1 then step2;
connection : HappensWhile[1] connect step2 to controller.Behaviour.operating1.toBothRed1;
connection : HappensDuring[1] connect dwell1 to controller.Behaviour.operating1.bothYellow1;
```

- A step of the counterexample is a reaction followed by one time advance. The reaction is a snapshot `stepN`, the advance a time slice `dwellN`, both portions of the trace instance (SysML 7.9.3 Time Slices and Snapshots, 8.4.5.2 Occurrence Usages). A snapshot takes no time, a time slice may (KerML 9.2.4.2.13 Occurrence). A step without a fired transition has no snapshot.
- `step0` is initialization and coincides with the start of the trace instance, `self.startShot` (KerML 9.2.4.2.13).
- Each dwell lasts one time step, asserted with `DurationOf` (SysML 7.20.3 Assert Constraint Usages, 9.8.8.2.4 DurationOf).
- Snapshots and time slices alternate with `HappensJustBefore` successions: nothing happens between the two ends (SysML 7.13.5 Successions as Usages, KerML 9.2.4.2.4 HappensJustBefore). The library relates an occurrence's start snapshot, middle time slice and end snapshot the same way.
- A transition occurrence coincides with the snapshot of its step, `HappensWhile`, so a firing takes no time (KerML 9.2.4.2.6 HappensWhile).
- A dwell lies within each leaf state active during it, `HappensDuring` with the dwell first (KerML 9.2.4.2.3 HappensDuring).

## Open points

- A connection usage is typed by association structures and is a link object (SysML 8.3.13.4, 8.4.9.2), while `HappensWhile` and `HappensDuring` are associations disjoint from occurrences (KerML 9.2.4.2.5 HappensLink). Read literally, `connection : HappensWhile` has an empty type. SysML has no other connector usage, and the standard library writes the same form (`FlowConnections`, `Items::Touches`), so the trace follows the library.
- An entry action belongs to its state, not to the transition entering it (SysML 9.2.11.2.1 StateAction). A send nested in a transition and the same send inherited from the target state's entry action are one send only if both are taken to happen at the step; the trace does not record whether a send came from an exit, an effect or an entry action.
- `new Toggle()` follows the model and the current pilot grammar; the 2024-12 notation for constructing an instance is `Toggle()` (KerML 8.4.4.9.4 Invocation Expressions).
