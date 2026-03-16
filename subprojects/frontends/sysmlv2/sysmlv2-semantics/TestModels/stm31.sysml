private import ScalarValues::Integer;

item def A;
item def B;
item def C;
item def D;
item def X;
item def E;

port def OutputEvents {
    out item a : A;
    out item b : B;
    out item c : C;
    out item d : D;
    out item x : X;
}

port def InputEvents {
    in item e : E;
}

part def Stm31 {
    attribute i : Integer default 0;
    port input : InputEvents;
    port output : OutputEvents;

    exhibit state Behaviour {
        state S1 {
            entry action {
                send A() via output;
            }
            exit action {
                assign i := i+1;
                send B() via output;
            }
        }
        state S2 {
            entry action {
                assign i := i*2;
                send C() via output;
            }
            exit action {
                send D() via output;
            }
        }
        transition first start then S1;
        
        transition
        first S1
        accept : E via input
        do action {
            assign i := i + 1;
            send D() via output;
        } 
        then S2;
    }
}

verification def S1Reachable {
    subject system : Stm31;

    objective {
        verify requirement {
            require constraint {
                eventually(
                    isStateActive(system.Behaviour.S1)
                )
            }
        }
    }
}

verification def S2Reachable {
    subject system : Stm31;

    objective {
        verify requirement {
            require constraint {
                eventually(
                    isStateActive(system.Behaviour.S2)
                )
            }
        }
    }
}
