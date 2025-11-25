private import ScalarValues::Integer;
private import ScalarValues::Boolean;
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

            transition first BothYellow accept after 1[s] do action {
                send Toggle() via controlA;
                send Toggle() via controlB;
            } then BothRed;

            state BothRed;

            transition first BothRed accept after 2[s] do action {
                send Toggle() via controlA;
            } then TrafficOnA;

            state TrafficOnA;

            transition first TrafficOnA accept after 2[s] do action {
                send Toggle() via controlA;
            } then StoppingA;
            
            state StoppingA;

            transition first StoppingA accept after 1[s] do action {
                send Toggle() via controlA;
                send Toggle() via controlB;
            } then TrafficOnB;
            
            state TrafficOnB;
            
            transition first TrafficOnB accept after 2[s] do action {
                send Toggle() via controlB;
            } then StoppingB;
            
            state StoppingB;
            
            transition first StoppingB accept after 1[s] do action {
                send Toggle() via controlA;
                send Toggle() via controlB;
            } then TrafficOnA;
        }

        transition first Operating accept Police via control then Interrupted;
        transition first Interrupted accept Police via control then Operating;

        state Interrupted {
            entry action {
                send Police() via controlA;
                send Police() via controlB;
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
                entry send YellowL() via lightControl;
            }

            transition first Yellow accept Toggle via control then Red;

            state Red {
                entry send RedL() via lightControl;
            }

            transition first Red accept Toggle via control then Green;

            state Green {
                entry send YellowL() via lightControl;
            }

            transition first Green accept Toggle via control then Yellow;
        }

        transition first Normal accept Police via control then Interrupted;
        transition first Interrupted accept Police via control then Normal;

        state Interrupted {
            transition first start then Yellow;

            state Yellow {
                entry send YellowL() via lightControl;
            }

            transition first Yellow accept after 1 [s] then Black;
            transition first Black accept after 1 [s] then Yellow;

            state Black {
                entry send YellowL() via lightControl;
            }
        }
    }
}

part def CrossroadSystem {
    port control : CentralControl;

    part controller: CentralController;
    part trafficLightA: TrafficLightController;
    part trafficLightB: TrafficLightController;

    bind control = controller.control; 

    flow from controller.controlA to trafficLightA.control;
    flow from controller.controlB to trafficLightB.control;
}



// Helper functions for verification
calc def eventually {
    in op: Boolean[1];
    return : Boolean[1];
}
calc def isStateActive { 
    in s: SysML::StateUsage[1];
    return : Boolean[1];
}

verification def ControllerOperatingReachable {
    subject system : CrossroadSystem;

    objective a {
        verify requirement  a {
            require constraint a  {
                // eventually(
                isStateActive(system.controller.Behaviour.Operating)
                // )
            }
        }
    }
}

verification def ControllerOperatingBothYellowReachable {
    subject system : CrossroadSystem;

    objective {
        verify requirement {
            require constraint {
                // eventually(
                isStateActive(system.controller.Behaviour.Operating.BothYellow)
                // )
            }
        }
    }
}
