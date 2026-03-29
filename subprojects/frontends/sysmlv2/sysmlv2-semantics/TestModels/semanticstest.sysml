private import ScalarValues::Integer;
private import ScalarValues::Boolean;
private import SI::s;

// ── Shared item and port definitions ──────────────────────────────────────────

item def EventE;
item def EventF;
item def EventG;
item def MessageM;

port def PortE { in item e : EventE; }
port def PortFG { in item f : EventF; in item g : EventG; }
port def PortMOut { out item m : MessageM; }

// ── 1. EntryActionModel ───────────────────────────────────────────────────────────
// Verifies that a state's entry action fires when the state is activated.

part def EntryActionModel {
    attribute entered : Boolean default false;

    exhibit state Behaviour {
        state S1 {
            entry action { assign entered := true; }
        }
        transition first start then S1;
    }
}

verification def EntryActionModelEntered {
    subject system : EntryActionModel;
    objective {
        verify requirement {
            require constraint { eventually(system.entered == true) }
        }
    }
}

// ── 2. ExitAction ────────────────────────────────────────────────────────────
// Verifies that a state's exit action fires when the state is left.

part def ExitActionModel {
    attribute exited : Boolean default false;
    port input : PortE;

    exhibit state Behaviour {
        state S1 {
            exit action { assign exited := true; }
        }
        state S2;
        transition first start then S1;
        transition first S1 accept : EventE via input then S2;
    }
}

verification def ExitActionExited {
    subject system : ExitActionModel;
    objective {
        verify requirement {
            require constraint { eventually(system.exited == true) }
        }
    }
}

// ── 3. TransitionDoActionModel ────────────────────────────────────────────────────
// Verifies that a transition's do-action fires when the transition is taken.

part def TransitionDoActionModel {
    attribute actionFired : Boolean default false;
    port input : PortE;

    exhibit state Behaviour {
        state S1;
        state S2;
        transition first start then S1;
        transition
            first S1
            accept : EventE via input
            do action { assign actionFired := true; }
            then S2;
    }
}

verification def TransitionDoActionModelFired {
    subject system : TransitionDoActionModel;
    objective {
        verify requirement {
            require constraint { eventually(system.actionFired == true) }
        }
    }
}

// ── 4. HierarchicalEntryModel ─────────────────────────────────────────────────────
// Verifies that entering a composite state activates its initial substate.

part def HierarchicalEntryModel {
    exhibit state Behaviour {
        state Outer {
            state Inner;
            transition first start then Inner;
        }
        transition first start then Outer;
    }
}

verification def HierarchicalEntryModelInner {
    subject system : HierarchicalEntryModel;
    objective {
        verify requirement {
            require constraint { eventually(isStateActive(system.Behaviour.Outer.Inner)) }
        }
    }
}

// ── 5. HierarchicalExitModel ──────────────────────────────────────────────────────
// Verifies that a transition from a composite state fires the state's exit action
// and reaches the target state, even when the active inner state has no own transitions.

part def HierarchicalExitModel {
    attribute outerExited : Boolean default false;
    port input : PortE;

    exhibit state Behaviour {
        state Outer {
            state S1;
            transition first start then S1;
            exit action { assign outerExited := true; }
        }
        state Done;
        transition first start then Outer;
        transition first Outer accept : EventE via input then Done;
    }
}

verification def HierarchicalExitModelDone {
    subject system : HierarchicalExitModel;
    objective {
        verify requirement {
            require constraint { eventually(isStateActive(system.Behaviour.Done)) }
        }
    }
}

verification def HierarchicalExitModelExited {
    subject system : HierarchicalExitModel;
    objective {
        verify requirement {
            require constraint { eventually(system.outerExited == true) }
        }
    }
}

// ── 6. TransitionPriorityModel ────────────────────────────────────────────────────
// Verifies bottom-up (inner-first) transition priority: when both an inner
// transition and an enclosing transition accept the same event, the inner one fires.

part def TransitionPriorityModel {
    attribute innerFired : Boolean default false;
    attribute outerFired : Boolean default false;
    port input : PortE;

    exhibit state Behaviour {
        state Outer {
            state Inner;
            state InnerDone;
            transition first start then Inner;
            transition
                first Inner
                accept : EventE via input
                do action { assign innerFired := true; }
                then InnerDone;
        }
        state OuterDone;
        transition first start then Outer;
        transition
            first Outer
            accept : EventE via input
            do action { assign outerFired := true; }
            then OuterDone;
    }
}

// Inner transition fires first (from Inner, E takes Inner→InnerDone).
verification def TransitionPriorityModelInnerFires {
    subject system : TransitionPriorityModel;
    objective {
        verify requirement {
            require constraint { eventually(system.innerFired == true) }
        }
    }
}

// Inner→InnerDone is reachable (would not be so under top-down priority).
verification def TransitionPriorityModelInnerDoneReachable {
    subject system : TransitionPriorityModel;
    objective {
        verify requirement {
            require constraint { eventually(isStateActive(system.Behaviour.Outer.InnerDone)) }
        }
    }
}

// Once InnerDone is active (no inner transition on E), the outer transition fires.
verification def TransitionPriorityModelOuterFires {
    subject system : TransitionPriorityModel;
    objective {
        verify requirement {
            require constraint { eventually(system.outerFired == true) }
        }
    }
}

// Safety: the outer transition never fires while Inner is the active inner state.
verification def TransitionPriorityModelBottomUpSafety {
    subject system : TransitionPriorityModel;
    objective {
        verify requirement {
            require constraint {
                mustAlways(
                    not (isStateActive(system.Behaviour.Outer.Inner)
                         and system.outerFired == true)
                )
            }
        }
    }
}

// ── 7. ParallelRegionsModel ───────────────────────────────────────────────────────
// Verifies that two concurrent regions execute independently and can both advance.

part def ParallelRegionsModel {
    port input : PortFG;

    exhibit state Behaviour parallel {
        state R1 {
            state A;
            state B;
            transition first start then A;
            transition first A accept : EventF via input then B;
        }
        state R2 {
            state C;
            state D;
            transition first start then C;
            transition first C accept : EventG via input then D;
        }
    }
}

verification def ParallelRegionsModelR1B {
    subject system : ParallelRegionsModel;
    objective {
        verify requirement {
            require constraint { eventually(isStateActive(system.Behaviour.R1.B)) }
        }
    }
}

verification def ParallelRegionsModelR2D {
    subject system : ParallelRegionsModel;
    objective {
        verify requirement {
            require constraint { eventually(isStateActive(system.Behaviour.R2.D)) }
        }
    }
}

verification def ParallelRegionsModelBothAdvanced {
    subject system : ParallelRegionsModel;
    objective {
        verify requirement {
            require constraint {
                eventually(
                    isStateActive(system.Behaviour.R1.B)
                    and isStateActive(system.Behaviour.R2.D)
                )
            }
        }
    }
}

// ── 8. TimeoutTransitionModel ─────────────────────────────────────────────────────
// Verifies that an AcceptTimeoutAction triggers after the specified number of time steps.

part def TimeoutTransitionModel {
    exhibit state Behaviour {
        state Waiting;
        state Done;
        transition first start then Waiting;
        transition first Waiting accept after 2 [s] then Done;
    }
}

verification def TimeoutTransitionModelDone {
    subject system : TimeoutTransitionModel;
    objective {
        verify requirement {
            require constraint { eventually(isStateActive(system.Behaviour.Done)) }
        }
    }
}

// ── 9. GuardedTransitionModel ─────────────────────────────────────────────────────
// Verifies that integer guards correctly control which transition fires.
// Uses a self-transition loop to increment a counter until the exit guard is satisfied.

part def GuardedTransitionModel {
    attribute counter : Integer default 0;

    exhibit state Behaviour {
        state Counting;
        state Done;
        transition first start then Counting;
        transition
            first Counting
            if counter < 3
            do action { assign counter := counter + 1; }
            then Counting;
        transition
            first Counting
            if counter >= 3
            then Done;
    }
}

verification def GuardedTransitionModelDone {
    subject system : GuardedTransitionModel;
    objective {
        verify requirement {
            require constraint { eventually(isStateActive(system.Behaviour.Done)) }
        }
    }
}

verification def GuardedTransitionModelCount {
    subject system : GuardedTransitionModel;
    objective {
        verify requirement {
            require constraint { eventually(system.counter >= 3) }
        }
    }
}

// ── 10. ItemFlow ─────────────────────────────────────────────────────────────
// Verifies that an item sent by one sub-part is delivered to another via a flow.

part def SenderPartModel {
    port output : PortMOut;
    exhibit state Behaviour {
        state Idle;
        state Sent;
        transition first start then Idle;
        transition
            first Idle
            do action { send MessageM() via output; }
            then Sent;
    }
}

part def ReceiverPartModel {
    attribute received : Boolean default false;
    port input : ~PortMOut;
    exhibit state Behaviour {
        state Idle;
        state Got {
            entry action { assign received := true; }
        }
        transition first start then Idle;
        transition first Idle accept : MessageM via input then Got;
    }
}

part def ItemFlowModel {
    part sender : SenderPartModel;
    part receiver : ReceiverPartModel;
    flow from sender.output to receiver.input;
}

verification def ItemFlowReceived {
    subject system : ItemFlowModel;
    objective {
        verify requirement {
            require constraint { eventually(isStateActive(system.receiver.Behaviour.Got)) }
        }
    }
}

verification def ItemFlowAttribute {
    subject system : ItemFlowModel;
    objective {
        verify requirement {
            require constraint { eventually(system.receiver.received == true) }
        }
    }
}

// ── 11. CrossHierarchyTransitionModel ─────────────────────────────────────────────
// Active configuration Outer.Middle.Inner → Other.Deep.
// Transition is from the outer boundary (Outer), so exit walks all three levels
// (Inner, Middle, Outer) and entry walks two levels (Other, Deep).
// Verifies that exit actions along the full chain fire and that the new deep
// target state is correctly activated.

part def CrossHierarchyTransitionModel {
    attribute middleExited : Boolean default false;
    attribute outerExited : Boolean default false;
    attribute otherEntered : Boolean default false;
    attribute deepEntered : Boolean default false;
    port input : PortE;

    exhibit state Behaviour {
        state Outer {
            state Middle {
                state Inner;
                transition first start then Inner;
                exit action { assign middleExited := true; }
            }
            transition first start then Middle;
            exit action { assign outerExited := true; }
        }
        state Other {
            state Deep {
                entry action { assign deepEntered := true; }
            }
            entry action { assign otherEntered := true; }
            transition first start then Deep;
        }
        transition first start then Outer;
        transition first Outer accept : EventE via input then Other;
    }
}

// Three-level deep initial state is reachable (active config before transition).
verification def CrossHierarchyInitialDeep {
    subject system : CrossHierarchyTransitionModel;
    objective {
        verify requirement {
            require constraint { eventually(isStateActive(system.Behaviour.Outer.Middle.Inner)) }
        }
    }
}

// After the cross-hierarchy transition, the two-level deep target is active.
verification def CrossHierarchyTargetDeep {
    subject system : CrossHierarchyTransitionModel;
    objective {
        verify requirement {
            require constraint { eventually(isStateActive(system.Behaviour.Other.Deep)) }
        }
    }
}

// Both exit actions along the departure chain (Middle, Outer) fire.
verification def CrossHierarchyExitChain {
    subject system : CrossHierarchyTransitionModel;
    objective {
        verify requirement {
            require constraint {
                eventually(system.middleExited == true and system.outerExited == true)
            }
        }
    }
}

// Both entry actions along the arrival chain (Other, Deep) fire.
verification def CrossHierarchyEntryChain {
    subject system : CrossHierarchyTransitionModel;
    objective {
        verify requirement {
            require constraint {
                eventually(system.otherEntered == true and system.deepEntered == true)
            }
        }
    }
}

// ── 12. ExitActionOrderModel ──────────────────────────────────────────────────────
// When leaving a 3-level hierarchy, exit actions fire innermost-first.
// Each exit action captures the current value of a step counter, then increments it.
// Correct order: Inner exit (step=0) → Middle exit (step=1) → Outer exit (step=2).
// Implementation detail: State.exit() calls deactivateRecursive(false) which
// deactivates children first, then exitRecursive() walks upward calling deactivate()
// on each ancestor up to (but not including) the commonState.

part def ExitActionOrderModel {
    attribute step : Integer default 0;
    attribute innerExitStep : Integer default 0;
    attribute middleExitStep : Integer default 0;
    attribute outerExitStep : Integer default 0;
    port input : PortE;

    exhibit state Behaviour {
        state Outer {
            state Middle {
                state Inner {
                    exit action { assign innerExitStep := step; assign step := step + 1; }
                }
                transition first start then Inner;
                exit action { assign middleExitStep := step; assign step := step + 1; }
            }
            transition first start then Middle;
            exit action { assign outerExitStep := step; assign step := step + 1; }
        }
        state Done;
        transition first start then Outer;
        transition first Outer accept : EventE via input then Done;
    }
}

verification def ExitActionOrderModelInnerBeforeMiddle {
    subject system : ExitActionOrderModel;
    objective {
        verify requirement {
            require constraint { eventually(system.innerExitStep < system.middleExitStep) }
        }
    }
}

verification def ExitActionOrderModelMiddleBeforeOuter {
    subject system : ExitActionOrderModel;
    objective {
        verify requirement {
            require constraint { eventually(system.middleExitStep < system.outerExitStep) }
        }
    }
}

// ── 13. EntryActionOrderModel ─────────────────────────────────────────────────────
// When entering a 3-level hierarchy, entry actions fire outermost-first.
// Implementation detail: State.enter() calls enterRecursive(), which recurses UP
// first (toward commonState), then calls activate() (which fires entryAction) on the
// way back DOWN. activateRecursive() then fires the start transition into the next level.

part def EntryActionOrderModel {
    attribute step : Integer default 0;
    attribute targetEntryStep : Integer default 0;
    attribute middleEntryStep : Integer default 0;
    attribute innerEntryStep : Integer default 0;
    port input : PortE;

    exhibit state Behaviour {
        state Source;
        state Target {
            state Middle {
                state Inner {
                    entry action { assign innerEntryStep := step; assign step := step + 1; }
                }
                entry action { assign middleEntryStep := step; assign step := step + 1; }
                transition first start then Inner;
            }
            entry action { assign targetEntryStep := step; assign step := step + 1; }
            transition first start then Middle;
        }
        transition first start then Source;
        transition first Source accept : EventE via input then Target;
    }
}

verification def EntryActionOrderModelTargetBeforeMiddle {
    subject system : EntryActionOrderModel;
    objective {
        verify requirement {
            require constraint { eventually(system.targetEntryStep < system.middleEntryStep) }
        }
    }
}

verification def EntryActionOrderModelMiddleBeforeInner {
    subject system : EntryActionOrderModel;
    objective {
        verify requirement {
            require constraint { eventually(system.middleEntryStep < system.innerEntryStep) }
        }
    }
}

// ── 14. ParallelStateExitModel ────────────────────────────────────────────────────
// Exiting a parallel state must deactivate ALL concurrent regions.
// Implementation detail: ParallelState.exit() calls deactivateRecursive(false) which
// recursively deactivates every sub-region with _deactivate=true.

part def ParallelStateExitModel {
    port input : PortE;

    exhibit state Behaviour {
        state Running parallel {
            state R1 {
                state R1On;
                transition first start then R1On;
            }
            state R2 {
                state R2On;
                transition first start then R2On;
            }
        }
        state Done;
        transition first start then Running;
        transition first Running accept : EventE via input then Done;
    }
}

// Both regions are concurrently active before the exit transition.
verification def ParallelStateExitModelBothActive {
    subject system : ParallelStateExitModel;
    objective {
        verify requirement {
            require constraint {
                eventually(
                    isStateActive(system.Behaviour.Running.R1.R1On)
                    and isStateActive(system.Behaviour.Running.R2.R2On)
                )
            }
        }
    }
}

// Done is reachable (parallel state can be exited).
verification def ParallelStateExitModelDone {
    subject system : ParallelStateExitModel;
    objective {
        verify requirement {
            require constraint { eventually(isStateActive(system.Behaviour.Done)) }
        }
    }
}

// After exiting the parallel state, neither region is active (safety).
verification def ParallelStateExitModelR1Deactivated {
    subject system : ParallelStateExitModel;
    objective {
        verify requirement {
            require constraint {
                mustAlways(
                    not (isStateActive(system.Behaviour.Done)
                         and isStateActive(system.Behaviour.Running.R1.R1On))
                )
            }
        }
    }
}

// ── 15. ParallelNestedInSequentialModel ───────────────────────────────────────────
// A parallel state nested inside a sequential state.
// Tests ParallelState.enterRecursive() when parent is a sequential State (not the root),
// and ParallelState.activate() setting the parent's activeState correctly.

part def ParallelNestedInSequentialModel {
    port input : PortE;

    exhibit state Behaviour {
        state Outer {
            state Inner parallel {
                state P1 {
                    state P1A;
                    transition first start then P1A;
                }
                state P2 {
                    state P2A;
                    transition first start then P2A;
                }
            }
            transition first start then Inner;
        }
        state Done;
        transition first start then Outer;
        transition first Outer accept : EventE via input then Done;
    }
}

// Both parallel sub-regions are active while inside the sequential Outer state.
verification def ParallelNestedBothActive {
    subject system : ParallelNestedInSequentialModel;
    objective {
        verify requirement {
            require constraint {
                eventually(
                    isStateActive(system.Behaviour.Outer.Inner.P1.P1A)
                    and isStateActive(system.Behaviour.Outer.Inner.P2.P2A)
                )
            }
        }
    }
}

// The sequential state can be exited even when a parallel state is active inside.
verification def ParallelNestedExitDone {
    subject system : ParallelNestedInSequentialModel;
    objective {
        verify requirement {
            require constraint { eventually(isStateActive(system.Behaviour.Done)) }
        }
    }
}
