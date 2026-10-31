private import ScalarValues::Boolean;

calc def <EF> eventually {
    in op: Boolean[1];
    return : Boolean[1];
}

calc def <AG> mustAlways {
    in op: Boolean[1];
    return : Boolean[1];
}

calc def isStateActive {
    in s: SysML::StateUsage[1];
    return : Boolean[1];
}

item def Press;
item def Toggle;

port def ButtonPort {
    in item press : Press;
}

port def TogglePort {
    out item toggle : Toggle;
}

part def Switch {
    port button : ButtonPort;
    port output : TogglePort;

    exhibit state Behaviour {
        transition first start then Off;

        state Off;
        state On;

        transition turnOn first Off
            accept Press via button
            do action { send new Toggle() via output; }
            then On;

        transition turnOff first On
            accept Press via button
            do action { send new Toggle() via output; }
            then Off;
    }
}

part def Lamp {
    port input : ~TogglePort;

    exhibit state Behaviour {
        transition first start then Dark;

        state Dark;
        state Lit;

        transition lightUp first Dark accept Toggle via input then Lit;
        transition goDark first Lit accept Toggle via input then Dark;
    }
}

part def Room {
    port button : ButtonPort;

    part switch : Switch;
    part lamp : Lamp;

    bind button = switch.button;

    flow from switch.output to lamp.input;
}

verification def LampLitReachable {
    subject system : Room;

    objective {
        verify requirement {
            require constraint {
                eventually(
                    isStateActive(system.lamp.Behaviour.Lit)
                )
            }
        }
    }
}

verification def LampNeverLitViolated {
    subject system : Room;

    objective {
        verify requirement {
            require constraint {
                mustAlways(not
                    isStateActive(system.lamp.Behaviour.Lit)
                )
            }
        }
    }
}

verification def LampLitOnlyWhileSwitchOnViolated {
    subject system : Room;

    objective {
        verify requirement {
            require constraint {
                mustAlways(not(
                    isStateActive(system.lamp.Behaviour.Lit)
                    and isStateActive(system.switch.Behaviour.Off)
                ))
            }
        }
    }
}
