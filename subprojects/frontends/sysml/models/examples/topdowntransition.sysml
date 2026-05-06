private import ScalarValues::Integer;
private import ScalarValues::Boolean;
private import SI::s;

// NOTE: use with the TopDownLibrary

item def VarEventE;
item def VarEventF;

port def VarPortE { in item e : VarEventE; }
port def VarPortF { in item f : VarEventF; }

part def TransitionPriorityTopDownModel {
    attribute innerFired : Boolean default false;
    attribute outerFired : Boolean default false;
    port input : VarPortE;

    exhibit state Behaviour {
        state Outer {
            state Inner;
            state InnerDone;
            transition first start then Inner;
            transition
                first Inner
                accept : VarEventE via input
                do action { assign innerFired := true; }
                then InnerDone;
        }
        state OuterDone;
        transition first start then Outer;
        transition
            first Outer
            accept : VarEventE via input
            do action { assign outerFired := true; }
            then OuterDone;
    }
}

verification def OuterTransitionCanFireTestCase {
    subject system : TransitionPriorityTopDownModel;
    objective {
        verify requirement {
            require constraint { eventually (system.outerFired) }
        }
    }
}

verification def OuterDoneReachableTestCase {
    subject system : TransitionPriorityTopDownModel;
    objective {
        verify requirement {
            require constraint { eventually (isStateActive(system.Behaviour.OuterDone)) }
        }
    }
}

verification def InnerTransitionNeverFiresTestCase {
    subject system : TransitionPriorityTopDownModel;
    objective {
        verify requirement {
            require constraint { mustAlways(system.innerFired == false) }
        }
    }
}

verification def InnerDoneNotReachableTestCase {
    subject system : TransitionPriorityTopDownModel;
    objective {
        verify requirement {
            require constraint { mustAlways(not isStateActive(system.Behaviour.Outer.InnerDone)) }
        }
    }
}
