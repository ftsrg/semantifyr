private import ScalarValues::Integer;
private import ScalarValues::Boolean;

item def F;
item def G;

port def InputEvents {
    in item f : F;
    in item g : G;
}

part def Stm21 {
    port input : InputEvents;

    exhibit state Behaviour parallel {
        state R1 {
            state S11;
            state S12;

            transition first start then S11;
            transition
                first S11
                accept : F via input
                then S12;
        }
        state R2 {
            state S13;
            state S14;
            transition first start then S13;
            transition
                first S13
                accept : G via input
                then S14;
        }
    }
}

verification def S11Reachable {
    subject system : Stm21;

    objective {
        verify requirement {
            require constraint {
                eventually(
                    isStateActive(system.Behaviour.R1.S11)
                )
            }
        }
    }
}

verification def S12Reachable {
    subject system : Stm21;

    objective {
        verify requirement {
            require constraint {
                eventually(
                    isStateActive(system.Behaviour.R1.S12)
                )
            }
        }
    }
}

verification def S13Reachable {
    subject system : Stm21;

    objective {
        verify requirement {
            require constraint {
                eventually(
                    isStateActive(system.Behaviour.R2.S13)
                )
            }
        }
    }
}

verification def S14Reachable {
    subject system : Stm21;

    objective {
        verify requirement {
            require constraint {
                eventually(
                    isStateActive(system.Behaviour.R2.S14)
                )
            }
        }
    }
}
