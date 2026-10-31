private import ScalarValues::Integer;

// Exactly one initial transition is enabled at each level; guards decide which.
part def GuardedInitialTransitionModel {
    attribute mode : Integer default 0;

    exhibit state Behaviour {
        transition first start if mode == 1 then High;
        transition first start if mode == 0 then Low;

        state High;
        state Low;

        transition first Low then Composite;

        state Composite {
            transition first start if mode == 1 then Left;
            transition first start if mode == 0 then Right;

            state Left;
            state Right;
        }
    }
}

verification def GuardedInitialTransitionEntersLowTestCase {
    subject system : GuardedInitialTransitionModel;
    objective {
        verify requirement {
            require constraint { eventually(isStateActive(system.Behaviour.Low)) }
        }
    }
}

verification def GuardedInitialTransitionNeverEntersHighTestCase {
    subject system : GuardedInitialTransitionModel;
    objective {
        verify requirement {
            require constraint { not eventually(isStateActive(system.Behaviour.High)) }
        }
    }
}

verification def GuardedNestedInitialTransitionEntersRightTestCase {
    subject system : GuardedInitialTransitionModel;
    objective {
        verify requirement {
            require constraint { eventually(isStateActive(system.Behaviour.Composite.Right)) }
        }
    }
}

verification def GuardedNestedInitialTransitionNeverEntersLeftTestCase {
    subject system : GuardedInitialTransitionModel;
    objective {
        verify requirement {
            require constraint { not eventually(isStateActive(system.Behaviour.Composite.Left)) }
        }
    }
}
