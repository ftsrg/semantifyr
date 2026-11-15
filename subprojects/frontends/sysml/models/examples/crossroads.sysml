doc
/*
 * Two traffic lights at a crossroads, cycled by one controller and interrupted by the police.
 * On a timer, the controller toggles each light from yellow to red, to green and back to yellow, so that the two roads get green in turn.
 * A police signal from outside interrupts the cycle and both lights blink yellow, until a second signal restarts it.
 * The cases ask whether the police can interrupt, whether the lights blink, whether road B gets green, and whether both roads can be green at once.
 */

private import SI::s;

item def Police;
item def Toggle;
item def RedL;
item def YellowL;
item def GreenL;
item def NoneL;

port def CentralControl {
    out item police : Police;
    out item toggle : Toggle;
}
port def LightControl {
    out item red : RedL;
    out item yellow : YellowL;
    out item green : GreenL;
    out item none : NoneL;
}

part def CentralController {
    port control : ~CentralControl;
    port controlA : CentralControl;
    port controlB : CentralControl;
    
    exhibit state Behaviour {
        transition first start then Operating;

        state Operating {
            transition first start then BothYellow;

            state BothYellow;

            // switch A to Red, B to Red
            transition first BothYellow accept after 1[s] do action {
                send new Toggle() via controlA;
                send new Toggle() via controlB;
            } then BothRed;

            state BothRed;

            // switch A to Green
            transition first BothRed accept after 2[s] do action {
                send new Toggle() via controlA;
            } then TrafficOnA;

            state TrafficOnA;

            // switch A to Yellow
            transition first TrafficOnA accept after 2[s] do action {
                send new Toggle() via controlA;
            } then StoppingA;
            
            state StoppingA;

            // switch A to Red, B to Green
            transition first StoppingA accept after 1[s] do action {
                send new Toggle() via controlA;
                send new Toggle() via controlB;
            } then TrafficOnB;
            
            state TrafficOnB;
            
            // switch B to Yellow
            transition first TrafficOnB accept after 2[s] do action {
                send new Toggle() via controlB;
            } then StoppingB;
            
            state StoppingB;
            
            // switch A to Green, B to Red
            transition first StoppingB accept after 1[s] do action {
                send new Toggle() via controlA;
                send new Toggle() via controlB;
            } then TrafficOnA;
        }

        transition first Operating accept Police via control then Interrupted;
        transition first Interrupted accept Police via control then Operating;

        state Interrupted {
            entry action {
                send new Police() via controlA;
                send new Police() via controlB;
            }
            exit action {
                send new Police() via controlA;
                send new Police() via controlB;
            }
        }
    }
}

part def TrafficLightController {
    port control : ~CentralControl;
    port lightControl : LightControl;

    exhibit state Behaviour {
        transition first start then Normal;

        state Normal {
            transition first start then Yellow;

            state Yellow {
                entry send new YellowL() via lightControl;
            }

            transition first Yellow accept Toggle via control then Red;

            state Red {
                entry send new RedL() via lightControl;
            }

            transition first Red accept Toggle via control then Green;

            state Green {
                entry send new GreenL() via lightControl;
            }

            transition first Green accept Toggle via control then Yellow;
        }

        transition first Normal accept Police via control then Interrupted;

        state Interrupted {
            transition first start then Yellow;

            // declared in the inner states, which fire before the flashing transitions of Interrupted
            state Yellow {
                entry send new YellowL() via lightControl;
                transition first Yellow accept Police via control then Normal;
            }

            transition first Yellow accept after 1 [s] then Black;
            transition first Black accept after 1 [s] then Yellow;

            state Black {
                entry send new NoneL() via lightControl;
                transition first Black accept Police via control then Normal;
            }
        }
    }
}

part def CrossroadSystem {
    port control : ~CentralControl;

    part controller: CentralController;
    part trafficLightA: TrafficLightController;
    part trafficLightB: TrafficLightController;

    bind control = controller.control; 

    flow from controller.controlA to trafficLightA.control;
    flow from controller.controlB to trafficLightB.control;
}

verification def PoliceCanInterrupt {
    subject system : CrossroadSystem;

    objective {
        verify requirement {
            require constraint {
                eventually(
                    isStateActive(system.controller.Behaviour.Interrupted)
                )
            }
        }
    }
}

verification def LightACanBlink {
    subject system : CrossroadSystem;

    objective {
        verify requirement {
            require constraint {
                eventually(
                    isStateActive(system.trafficLightA.Behaviour.Interrupted.Black)
                )
            }
        }
    }
}

verification def RoadBCanGetGreen {
    subject system : CrossroadSystem;

    objective {
        verify requirement {
            require constraint {
                eventually(
                    isStateActive(system.trafficLightB.Behaviour.Normal.Green)
                )
            }
        }
    }
}

verification def NeverBothGreen {
    subject system : CrossroadSystem;

    objective {
        verify requirement {
            require constraint {
                mustAlways( not(
                    isStateActive(system.trafficLightA.Behaviour.Normal.Green)
                    and isStateActive(system.trafficLightB.Behaviour.Normal.Green)
                ) )
            }
        }
    }
}
