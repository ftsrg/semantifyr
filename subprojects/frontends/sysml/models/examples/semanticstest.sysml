private import ScalarValues::Integer;
private import ScalarValues::Boolean;
private import SI::s;

item def EventE;
item def EventF;
item def EventG;
item def MessageM;

port def PortE { in item e : EventE; }
port def PortFG { in item f : EventF; in item g : EventG; }
port def PortMOut { out item m : MessageM; }
port def PortEFOut { out item e : EventE; out item f : EventF; }

part def EntryActionModel {
    attribute entered : Boolean := false;

    exhibit state Behaviour {
        state S1 {
            entry action { assign entered := true; }
        }
        transition first start then S1;
    }
}

verification def EntryActionFiresOnActivationTestCase {
    subject system : EntryActionModel;
    objective {
        verify requirement {
            require constraint { eventually(system.entered == true) }
        }
    }
}

verification def EntryFlagAlwaysSetWhileStateIsActiveTestCase {
    subject system : EntryActionModel;
    objective {
        verify requirement {
            require constraint { mustAlways(not (isStateActive(system.Behaviour.S1) and system.entered == false)) }
        }
    }
}

part def ExitActionModel {
    attribute exited : Boolean := false;
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

verification def ExitActionFiresOnDeactivationTestCase {
    subject system : ExitActionModel;
    objective {
        verify requirement {
            require constraint { eventually(system.exited == true) }
        }
    }
}

verification def ExitFlagAlwaysSetWhenSuccessorIsActiveTestCase {
    subject system : ExitActionModel;
    objective {
        verify requirement {
            require constraint { mustAlways(not (isStateActive(system.Behaviour.S2) and system.exited == false)) }
        }
    }
}

part def TransitionDoActionModel {
    attribute actionFired : Boolean := false;
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

verification def DoActionFiresOnTransitionTestCase {
    subject system : TransitionDoActionModel;
    objective {
        verify requirement {
            require constraint { eventually(system.actionFired == true) }
        }
    }
}

verification def DoActionAlwaysFiredWhenTargetIsActiveTestCase {
    subject system : TransitionDoActionModel;
    objective {
        verify requirement {
            require constraint { mustAlways(not (isStateActive(system.Behaviour.S2) and system.actionFired == false)) }
        }
    }
}

part def HierarchicalEntryModel {
    exhibit state Behaviour {
        state Outer {
            state Inner;
            transition first start then Inner;
        }
        transition first start then Outer;
    }
}

verification def CompositeEntryActivatesInitialSubstateTestCase {
    subject system : HierarchicalEntryModel;
    objective {
        verify requirement {
            require constraint { eventually(isStateActive(system.Behaviour.Outer.Inner)) }
        }
    }
}

verification def CompositeActiveAlwaysImpliesSubstateActiveTestCase {
    subject system : HierarchicalEntryModel;
    objective {
        verify requirement {
            require constraint { mustAlways(not (isStateActive(system.Behaviour.Outer) and not isStateActive(system.Behaviour.Outer.Inner))) }
        }
    }
}

part def HierarchicalExitModel {
    attribute outerExited : Boolean := false;
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

verification def CompositeExitReachesDoneStateTestCase {
    subject system : HierarchicalExitModel;
    objective {
        verify requirement {
            require constraint { eventually(isStateActive(system.Behaviour.Done)) }
        }
    }
}

verification def CompositeExitFiresExitActionTestCase {
    subject system : HierarchicalExitModel;
    objective {
        verify requirement {
            require constraint { eventually(system.outerExited == true) }
        }
    }
}

verification def ExitActionAlwaysFiredWhenDoneIsActiveTestCase {
    subject system : HierarchicalExitModel;
    objective {
        verify requirement {
            require constraint { mustAlways(not (isStateActive(system.Behaviour.Done) and system.outerExited == false)) }
        }
    }
}

part def TransitionPriorityModel {
    attribute innerFired : Boolean := false;
    attribute outerFired : Boolean := false;
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

verification def TransitionsHaveBottomUpPriorityTestCase {
    subject system : TransitionPriorityModel;
    objective {
        verify requirement {
            require constraint { eventually(system.innerFired == true) }
        }
    }
}

verification def InnerDoneReachableWithBottomUpPriorityTestCase {
    subject system : TransitionPriorityModel;
    objective {
        verify requirement {
            require constraint { eventually(isStateActive(system.Behaviour.Outer.InnerDone)) }
        }
    }
}

verification def OuterTransitionFiresAfterInnerExhaustedTestCase {
    subject system : TransitionPriorityModel;
    objective {
        verify requirement {
            require constraint { eventually(system.outerFired == true) }
        }
    }
}

verification def OuterTransitionBlockedWhileInnerEnabledTestCase {
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

verification def OuterDoneAlwaysImpliesInnerFiredFirstTestCase {
    subject system : TransitionPriorityModel;
    objective {
        verify requirement {
            require constraint { mustAlways(not (isStateActive(system.Behaviour.OuterDone) and system.innerFired == false)) }
        }
    }
}

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

verification def FirstParallelRegionAdvancesIndependentlyTestCase {
    subject system : ParallelRegionsModel;
    objective {
        verify requirement {
            require constraint { eventually(isStateActive(system.Behaviour.R1.B)) }
        }
    }
}

verification def SecondParallelRegionAdvancesIndependentlyTestCase {
    subject system : ParallelRegionsModel;
    objective {
        verify requirement {
            require constraint { eventually(isStateActive(system.Behaviour.R2.D)) }
        }
    }
}

verification def BothParallelRegionsCanAdvanceSimultaneouslyTestCase {
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

verification def RegionSubstatesAreMutuallyExclusiveTestCase {
    subject system : ParallelRegionsModel;
    objective {
        verify requirement {
            require constraint { mustAlways(not (isStateActive(system.Behaviour.R1.A) and isStateActive(system.Behaviour.R1.B))) }
        }
    }
}

part def TimeoutTransitionModel {
    exhibit state Behaviour {
        state Waiting;
        state Done;
        transition first start then Waiting;
        transition first Waiting accept after 2 [s] then Done;
    }
}

verification def TimeoutTransitionFiresAfterDelayTestCase {
    subject system : TimeoutTransitionModel;
    objective {
        verify requirement {
            require constraint { eventually(isStateActive(system.Behaviour.Done)) }
        }
    }
}

verification def WaitingAndDoneAreMutuallyExclusiveTestCase {
    subject system : TimeoutTransitionModel;
    objective {
        verify requirement {
            require constraint { mustAlways(not (isStateActive(system.Behaviour.Waiting) and isStateActive(system.Behaviour.Done))) }
        }
    }
}

part def GuardedTransitionModel {
    attribute counter : Integer := 0;

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

verification def GuardConditionGatesTransitionTestCase {
    subject system : GuardedTransitionModel;
    objective {
        verify requirement {
            require constraint { eventually(isStateActive(system.Behaviour.Done)) }
        }
    }
}

verification def GuardAccumulatesCounterTestCase {
    subject system : GuardedTransitionModel;
    objective {
        verify requirement {
            require constraint { eventually(system.counter >= 3) }
        }
    }
}

verification def DoneNotReachableBeforeGuardSatisfiedTestCase {
    subject system : GuardedTransitionModel;
    objective {
        verify requirement {
            require constraint { mustAlways(not (isStateActive(system.Behaviour.Done) and system.counter < 3)) }
        }
    }
}

part def SenderPartModel {
    port output : PortMOut;
    exhibit state Behaviour {
        state Idle;
        state Sent;
        transition first start then Idle;
        transition
            first Idle
            do action { send new MessageM() via output; }
            then Sent;
    }
}

part def ReceiverPartModel {
    attribute received : Boolean := false;
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

verification def ItemFlowDeliversToReceiverTestCase {
    subject system : ItemFlowModel;
    objective {
        verify requirement {
            require constraint { eventually(isStateActive(system.receiver.Behaviour.Got)) }
        }
    }
}

verification def ReceiverAttributeSetByItemFlowTestCase {
    subject system : ItemFlowModel;
    objective {
        verify requirement {
            require constraint { eventually(system.receiver.received == true) }
        }
    }
}

verification def ReceivedFlagAlwaysSetWhenGotIsActiveTestCase {
    subject system : ItemFlowModel;
    objective {
        verify requirement {
            require constraint { mustAlways(not (isStateActive(system.receiver.Behaviour.Got) and system.receiver.received == false)) }
        }
    }
}

part def CrossHierarchyTransitionModel {
    attribute middleExited : Boolean := false;
    attribute outerExited : Boolean := false;
    attribute otherEntered : Boolean := false;
    attribute deepEntered : Boolean := false;
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

verification def ThreeLevelDeepInitialStateReachableTestCase {
    subject system : CrossHierarchyTransitionModel;
    objective {
        verify requirement {
            require constraint { eventually(isStateActive(system.Behaviour.Outer.Middle.Inner)) }
        }
    }
}

verification def CrossHierarchyTransitionReachesTwoLevelDeepStateTestCase {
    subject system : CrossHierarchyTransitionModel;
    objective {
        verify requirement {
            require constraint { eventually(isStateActive(system.Behaviour.Other.Deep)) }
        }
    }
}

verification def CrossHierarchyExitActionsFireAtAllLevelsTestCase {
    subject system : CrossHierarchyTransitionModel;
    objective {
        verify requirement {
            require constraint {
                eventually(system.middleExited == true and system.outerExited == true)
            }
        }
    }
}

verification def CrossHierarchyEntryActionsFireAtAllLevelsTestCase {
    subject system : CrossHierarchyTransitionModel;
    objective {
        verify requirement {
            require constraint {
                eventually(system.otherEntered == true and system.deepEntered == true)
            }
        }
    }
}

verification def OuterAndOtherAreMutuallyExclusiveTestCase {
    subject system : CrossHierarchyTransitionModel;
    objective {
        verify requirement {
            require constraint { mustAlways(not (isStateActive(system.Behaviour.Outer) and isStateActive(system.Behaviour.Other))) }
        }
    }
}

part def ExitActionOrderModel {
    attribute step : Integer := 0;
    attribute innerExitStep : Integer := 0;
    attribute middleExitStep : Integer := 0;
    attribute outerExitStep : Integer := 0;
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

verification def InnerExitActionFiresBeforeMiddleExitActionTestCase {
    subject system : ExitActionOrderModel;
    objective {
        verify requirement {
            require constraint { eventually(system.innerExitStep < system.middleExitStep) }
        }
    }
}

verification def MiddleExitActionFiresBeforeOuterExitActionTestCase {
    subject system : ExitActionOrderModel;
    objective {
        verify requirement {
            require constraint { eventually(system.middleExitStep < system.outerExitStep) }
        }
    }
}

verification def MiddleNeverExitsBeforeInnerTestCase {
    subject system : ExitActionOrderModel;
    objective {
        verify requirement {
            require constraint { mustAlways(not (system.middleExitStep < system.innerExitStep)) }
        }
    }
}

verification def OuterNeverExitsBeforeMiddleTestCase {
    subject system : ExitActionOrderModel;
    objective {
        verify requirement {
            require constraint { mustAlways(not (system.outerExitStep < system.middleExitStep)) }
        }
    }
}

part def EntryActionOrderModel {
    attribute step : Integer := 0;
    attribute targetEntryStep : Integer := 0;
    attribute middleEntryStep : Integer := 0;
    attribute innerEntryStep : Integer := 0;
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

verification def OutermostEntryActionFiresBeforeMiddleEntryActionTestCase {
    subject system : EntryActionOrderModel;
    objective {
        verify requirement {
            require constraint { eventually(system.targetEntryStep < system.middleEntryStep) }
        }
    }
}

verification def MiddleEntryActionFiresBeforeInnermostEntryActionTestCase {
    subject system : EntryActionOrderModel;
    objective {
        verify requirement {
            require constraint { eventually(system.middleEntryStep < system.innerEntryStep) }
        }
    }
}

verification def MiddleNeverEntersBeforeTargetTestCase {
    subject system : EntryActionOrderModel;
    objective {
        verify requirement {
            require constraint { mustAlways(not (system.middleEntryStep < system.targetEntryStep)) }
        }
    }
}

verification def InnerNeverEntersBeforeMiddleTestCase {
    subject system : EntryActionOrderModel;
    objective {
        verify requirement {
            require constraint { mustAlways(not (system.innerEntryStep < system.middleEntryStep)) }
        }
    }
}

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

verification def BothParallelRegionsActiveBeforeExitTestCase {
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

verification def ParallelStateCanBeExitedTestCase {
    subject system : ParallelStateExitModel;
    objective {
        verify requirement {
            require constraint { eventually(isStateActive(system.Behaviour.Done)) }
        }
    }
}

verification def AllParallelRegionsDeactivatedOnExitTestCase {
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

verification def ParallelRegionsActiveInsideSequentialStateTestCase {
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

verification def SequentialStateWithNestedParallelExitableTestCase {
    subject system : ParallelNestedInSequentialModel;
    objective {
        verify requirement {
            require constraint { eventually(isStateActive(system.Behaviour.Done)) }
        }
    }
}

verification def DoneAndNestedParallelSubstatesAreMutuallyExclusiveTestCase {
    subject system : ParallelNestedInSequentialModel;
    objective {
        verify requirement {
            require constraint { mustAlways(not (isStateActive(system.Behaviour.Done) and isStateActive(system.Behaviour.Outer.Inner.P1.P1A))) }
        }
    }
}

part def SpontaneousTransitionModel {
    attribute transitioned : Boolean := false;

    exhibit state Behaviour {
        state S1;
        state S2;
        transition first start then S1;
        transition
            first S1
            accept when true
            do action { assign transitioned := true; }
            then S2;
    }
}

verification def SpontaneousTransitionReachesTargetTestCase {
    subject system : SpontaneousTransitionModel;
    objective {
        verify requirement {
            require constraint { eventually(isStateActive(system.Behaviour.S2)) }
        }
    }
}

verification def SpontaneousTransitionDoActionFiresTestCase {
    subject system : SpontaneousTransitionModel;
    objective {
        verify requirement {
            require constraint { eventually(system.transitioned == true) }
        }
    }
}

verification def SpontaneousDoActionAlwaysFiredWhenTargetIsActiveTestCase {
    subject system : SpontaneousTransitionModel;
    objective {
        verify requirement {
            require constraint { mustAlways(not (isStateActive(system.Behaviour.S2) and system.transitioned == false)) }
        }
    }
}

part def GuardedSpontaneousTransitionModel {
    attribute counter : Integer := 0;

    exhibit state Behaviour {
        state Incrementing;
        state Done;
        transition first start then Incrementing;
        transition
            first Incrementing
            accept when counter < 3
            do action { assign counter := counter + 1; }
            then Incrementing;
        transition
            first Incrementing
            accept when counter >= 3
            then Done;
    }
}

verification def GuardedSpontaneousTransitionReachesDoneTestCase {
    subject system : GuardedSpontaneousTransitionModel;
    objective {
        verify requirement {
            require constraint { eventually(isStateActive(system.Behaviour.Done)) }
        }
    }
}

verification def GuardedSpontaneousCounterReachesThresholdTestCase {
    subject system : GuardedSpontaneousTransitionModel;
    objective {
        verify requirement {
            require constraint { eventually(system.counter >= 3) }
        }
    }
}

verification def GuardedSpontaneousDoneNotReachableBeforeThresholdTestCase {
    subject system : GuardedSpontaneousTransitionModel;
    objective {
        verify requirement {
            require constraint { mustAlways(not (isStateActive(system.Behaviour.Done) and system.counter < 3)) }
        }
    }
}

// A transition from a state inside one region of a parallel state to a state outside it leaves the whole parallel state, as the keep-alive timeouts of orion_protocol do.
part def ParallelStateRegionExitModel {
    attribute parallelExited : Boolean := false;
    port input : PortE;

    exhibit state Behaviour {
        state Running parallel {
            exit action { assign parallelExited := true; }
            state R1 {
                state A;
                transition first start then A;
                transition first A accept : EventE via input then Done;
            }
            state R2 {
                state C;
                transition first start then C;
            }
        }
        state Done;
        transition first start then Running;
    }
}

verification def RegionTransitionFiresParallelExitActionTestCase {
    subject system : ParallelStateRegionExitModel;
    objective {
        verify requirement {
            require constraint { eventually(system.parallelExited == true) }
        }
    }
}

verification def RegionTransitionLeavesEveryRegionTestCase {
    subject system : ParallelStateRegionExitModel;
    objective {
        verify requirement {
            require constraint { mustAlways(not (isStateActive(system.Behaviour.Done) and isStateActive(system.Behaviour.Running.R2.C))) }
        }
    }
}

part def ParallelRegionExitActionModel {
    attribute regionExited : Boolean := false;
    attribute idleExited : Boolean := false;
    port input : PortE;

    exhibit state Behaviour {
        state Running parallel {
            state R1 {
                exit action { assign regionExited := true; }
                state A;
                transition first start then A;
            }
            state R2 {
                state C;
                transition first start then C;
            }
        }
        state Other {
            state Idle parallel {
                exit action { assign idleExited := true; }
                state R3 {
                    state E;
                    transition first start then E;
                }
            }
            state Busy;
            transition first start then Busy;
        }
        state Done;
        transition first start then Running;
        transition first Running accept : EventE via input then Other;
        transition first Other accept : EventE via input then Done;
    }
}

verification def RegionExitActionFiresWhenParallelStateExitsTestCase {
    subject system : ParallelRegionExitActionModel;
    objective {
        verify requirement {
            require constraint { eventually(system.regionExited == true) }
        }
    }
}

verification def InactiveParallelStateExitActionNeverFiresTestCase {
    subject system : ParallelRegionExitActionModel;
    objective {
        verify requirement {
            require constraint { mustAlways(system.idleExited == false) }
        }
    }
}

part def ExhibitedStateEntryActionModel {
    attribute entered : Boolean := false;
    attribute enteredBeforeS1 : Boolean := false;

    exhibit state Behaviour {
        entry action { assign entered := true; }
        state S1 {
            entry action { assign enteredBeforeS1 := entered; }
        }
        transition first start then S1;
    }
}

verification def ExhibitedStateEntryActionFiresTestCase {
    subject system : ExhibitedStateEntryActionModel;
    objective {
        verify requirement {
            require constraint { eventually(system.entered == true) }
        }
    }
}

verification def ExhibitedStateEntryActionPrecedesSubstateTestCase {
    subject system : ExhibitedStateEntryActionModel;
    objective {
        verify requirement {
            require constraint { mustAlways(not isStateActive(system.Behaviour.S1) or system.enteredBeforeS1) }
        }
    }
}

part def ParallelExitOrderModel {
    attribute order : Integer := 0;
    port input : PortE;

    exhibit state Behaviour {
        state Running parallel {
            exit action { assign order := order * 10 + 5; }
            state R1 {
                exit action { assign order := order * 10 + 2; }
                state A {
                    exit action { assign order := order * 10 + 1; }
                }
                transition first start then A;
                transition
                    first A
                    accept : EventE via input
                    do action { assign order := order * 10 + 6; }
                    then Done;
            }
            state R2 {
                exit action { assign order := order * 10 + 4; }
                state C {
                    exit action { assign order := order * 10 + 3; }
                }
                transition first start then C;
            }
        }
        state Done {
            entry action { assign order := order * 10 + 7; }
        }
        transition first start then Running;
    }
}

verification def ParallelExitReachesDoneTestCase {
    subject system : ParallelExitOrderModel;
    objective {
        verify requirement {
            require constraint { eventually(isStateActive(system.Behaviour.Done)) }
        }
    }
}

verification def ParallelExitRunsExitsBeforeEffectTestCase {
    subject system : ParallelExitOrderModel;
    objective {
        verify requirement {
            require constraint { mustAlways(not isStateActive(system.Behaviour.Done) or system.order == 1234567) }
        }
    }
}

part def RegionEntryActionModel {
    attribute regionEntered : Boolean := false;

    exhibit state Behaviour {
        state Running parallel {
            state R1 {
                entry action { assign regionEntered := true; }
                state A;
                transition first start then A;
            }
            state R2 {
                state C;
                transition first start then C;
            }
        }
        transition first start then Running;
    }
}

verification def RegionEntryActionFiresTestCase {
    subject system : RegionEntryActionModel;
    objective {
        verify requirement {
            require constraint { mustAlways(system.regionEntered == true) }
        }
    }
}

part def SharedItemModel {
    port input : PortE;

    exhibit state Behaviour {
        state Running parallel {
            state R1 {
                state A;
                state B;
                transition first start then A;
                transition first A accept : EventE via input then B;
                transition first B then A;
            }
            state R2 {
                state C;
                state D;
                transition first start then C;
                transition first C accept : EventE via input then D;
                transition first D then C;
            }
        }
        transition first start then Running;
    }
}

verification def ItemFiresOneRegionPerStepTestCase {
    subject system : SharedItemModel;
    objective {
        verify requirement {
            require constraint { mustAlways(not (isStateActive(system.Behaviour.Running.R1.B) and isStateActive(system.Behaviour.Running.R2.D))) }
        }
    }
}

verification def FirstRegionConsumesItemTestCase {
    subject system : SharedItemModel;
    objective {
        verify requirement {
            require constraint { eventually(isStateActive(system.Behaviour.Running.R1.B)) }
        }
    }
}

verification def SecondRegionConsumesItemTestCase {
    subject system : SharedItemModel;
    objective {
        verify requirement {
            require constraint { eventually(isStateActive(system.Behaviour.Running.R2.D)) }
        }
    }
}

part def InitSenderModel {
    port output : PortMOut;

    exhibit state Behaviour {
        state Sending {
            entry action { send new MessageM() via output; }
        }
        transition first start then Sending;
    }
}

part def InitSendModel {
    part sender : InitSenderModel;
    part receiver : ReceiverPartModel;
    flow from sender.output to receiver.input;
}

verification def InitSendArrivesTestCase {
    subject system : InitSendModel;
    objective {
        verify requirement {
            require constraint { eventually(isStateActive(system.receiver.Behaviour.Got)) }
        }
    }
}

part def LateReceiverModel {
    port input : ~PortMOut;

    exhibit state Behaviour {
        state Idle;
        state Waiting;
        state Ready;
        state Got;
        transition first start then Idle;
        transition first Idle then Waiting;
        transition first Waiting then Ready;
        transition first Ready accept : MessageM via input then Got;
    }
}

part def LateReceiverFlowModel {
    part sender : SenderPartModel;
    part receiver : LateReceiverModel;
    flow from sender.output to receiver.input;
}

verification def UnacceptedItemIsDroppedTestCase {
    subject system : LateReceiverFlowModel;
    objective {
        verify requirement {
            require constraint { mustAlways(not isStateActive(system.receiver.Behaviour.Got)) }
        }
    }
}

verification def ObjectiveConstraintReachableTestCase {
    subject system : EntryActionModel;
    objective {
        require constraint { eventually(system.entered == true) }
    }
}

verification def ObjectiveConstraintCheckedViolated {
    subject system : EntryActionModel;
    objective {
        require constraint { mustAlways(system.entered == false) }
    }
}

part def ExhibitedDoneModel {
    port input : PortE;
    attribute finished : Boolean := false;
    attribute exited : Boolean := false;

    exhibit state Behaviour {
        exit action { assign exited := true; }
        entry; then Running;
        state Running;
        transition first Running accept : EventE via input do assign finished := true then done;
    }
}

verification def ExhibitedStateActiveUntilDoneTestCase {
    subject system : ExhibitedDoneModel;
    objective {
        verify requirement {
            require constraint { mustAlways(system.finished or isStateActive(system.Behaviour)) }
        }
    }
}

verification def ExhibitedStateInactiveAfterDoneTestCase {
    subject system : ExhibitedDoneModel;
    objective {
        verify requirement {
            require constraint { mustAlways(not system.finished or not isStateActive(system.Behaviour)) }
        }
    }
}

verification def ExhibitedStateReachesDoneTestCase {
    subject system : ExhibitedDoneModel;
    objective {
        verify requirement {
            require constraint { eventually(isStateActive(system.Behaviour) == false) }
        }
    }
}

verification def ExhibitedStateExitActionRunsAtDoneTestCase {
    subject system : ExhibitedDoneModel;
    objective {
        verify requirement {
            require constraint { mustAlways(isStateActive(system.Behaviour) or system.exited) }
        }
    }
}

verification def ExhibitedStateStaysActiveViolated {
    subject system : ExhibitedDoneModel;
    objective {
        verify requirement {
            require constraint { mustAlways(isStateActive(system.Behaviour)) }
        }
    }
}

part def CrossRegionEntryModel {
    port input : PortE;
    attribute order : Integer := 0;

    exhibit state Behaviour {
        entry; then Idle;
        state Idle;
        state Running parallel {
            entry assign order := order * 10 + 1;
            state Left {
                entry assign order := order * 10 + 4;
                transition first start then Initial;
                state Initial {
                    entry assign order := order * 10 + 9;
                }
                state Target {
                    entry assign order := order * 10 + 5;
                }
            }
            state Right {
                entry assign order := order * 10 + 2;
                transition first start then Sibling;
                state Sibling {
                    entry assign order := order * 10 + 3;
                }
            }
        }
        transition first Idle accept : EventE via input then Running.Left.Target;
    }
}

verification def CrossRegionEntryReachesTargetTestCase {
    subject system : CrossRegionEntryModel;
    objective {
        verify requirement {
            require constraint { eventually(isStateActive(system.Behaviour.Running.Left.Target)) }
        }
    }
}

verification def CrossRegionEntryEntersSiblingRegionTestCase {
    subject system : CrossRegionEntryModel;
    objective {
        verify requirement {
            require constraint { mustAlways(not isStateActive(system.Behaviour.Running.Left.Target) or isStateActive(system.Behaviour.Running.Right.Sibling)) }
        }
    }
}

verification def CrossRegionEntryRunsEntriesInOrderTestCase {
    subject system : CrossRegionEntryModel;
    objective {
        verify requirement {
            require constraint { mustAlways(not isStateActive(system.Behaviour.Running.Left.Target) or system.order == 12345) }
        }
    }
}

verification def CrossRegionEntryInitialDescentViolated {
    subject system : CrossRegionEntryModel;
    objective {
        verify requirement {
            require constraint { mustAlways(not isStateActive(system.Behaviour.Running.Left.Target) or system.order == 12495) }
        }
    }
}

part def DerivedAttributeModel {
    attribute counter : Integer := 0;
    attribute doubled : Integer = counter * 2;

    exhibit state Behaviour {
        entry; then Counting;
        state Counting;
        transition first Counting if counter < 3 do assign counter := counter + 1 then Counting;
    }
}

verification def DerivedAttributeFollowsItsExpressionTestCase {
    subject system : DerivedAttributeModel;
    objective {
        verify requirement {
            require constraint { mustAlways(system.doubled == system.counter * 2) }
        }
    }
}

verification def DerivedAttributeChangesTestCase {
    subject system : DerivedAttributeModel;
    objective {
        verify requirement {
            require constraint { eventually(system.doubled == 6) }
        }
    }
}

verification def DerivedAttributeConstantViolated {
    subject system : DerivedAttributeModel;
    objective {
        verify requirement {
            require constraint { mustAlways(system.doubled == 0) }
        }
    }
}

part def UnvaluedAttributeModel {
    attribute level : Integer;
    attribute flag : Boolean;
    attribute initial : Integer := 7;
}

verification def UnvaluedAttributeMayStartAboveZeroTestCase {
    subject system : UnvaluedAttributeModel;
    objective {
        verify requirement {
            require constraint { eventually(system.level > 0) }
        }
    }
}

verification def UnvaluedAttributeMayStartBelowZeroTestCase {
    subject system : UnvaluedAttributeModel;
    objective {
        verify requirement {
            require constraint { eventually(system.level < 0) }
        }
    }
}

verification def UnvaluedBooleanMayStartTrueTestCase {
    subject system : UnvaluedAttributeModel;
    objective {
        verify requirement {
            require constraint { eventually(system.flag == true) }
        }
    }
}

verification def UnvaluedBooleanMayStartFalseTestCase {
    subject system : UnvaluedAttributeModel;
    objective {
        verify requirement {
            require constraint { eventually(system.flag == false) }
        }
    }
}

verification def InitialValueHoldsViolated {
    subject system : UnvaluedAttributeModel;
    objective {
        verify requirement {
            require constraint { mustAlways(system.initial == 8) }
        }
    }
}

part def DoubleSenderModel {
    port output : PortEFOut;

    exhibit state Behaviour {
        entry; then Idle;
        state Idle;
        state Sent {
            entry send new EventF() via output;
        }
        transition first Idle do action { send new EventE() via output; } then Sent;
    }
}

part def SlotReceiverModel {
    port input : ~PortEFOut;

    exhibit state Behaviour {
        entry; then Waiting;
        state Waiting;
        state GotE;
        state GotF;
        transition first Waiting accept : EventE via input then GotE;
        transition first Waiting accept : EventF via input then GotF;
    }
}

part def LastWinsModel {
    part sender : DoubleSenderModel;
    part receiver : SlotReceiverModel;
    flow from sender.output to receiver.input;
}

verification def LastItemOnOneSlotIsAcceptedTestCase {
    subject system : LastWinsModel;
    objective {
        verify requirement {
            require constraint { eventually(isStateActive(system.receiver.Behaviour.GotF)) }
        }
    }
}

verification def EarlierItemOnOneSlotIsNeverAcceptedTestCase {
    subject system : LastWinsModel;
    objective {
        verify requirement {
            require constraint { mustAlways(not isStateActive(system.receiver.Behaviour.GotE)) }
        }
    }
}
