private import ScalarValues::Boolean;

calc def <EF> eventually {
    in op: Boolean[1];
    return : Boolean[1];
}

calc def isStateActive {
    in state s[1];
    return : Boolean[1];
}

item def Press;

port def ButtonPort {
    in item press : Press;
}

part def Switch {
    port button : ButtonPort;

    exhibit state Behaviour {
        transition first start then Off;

        state Off;
        state On;

        transition turnOn first Off accept Press via button then On;
    }
}

verification def SwitchOnReachable {
    subject system : Switch;

    objective {
        verify requirement {
            require constraint {
                eventually(
                    isStateActive(system.Behaviour.On)
                )
            }
        }
    }
}
