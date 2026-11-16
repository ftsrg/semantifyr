/*
 * The lift with a light curtain in its door: an obstacle in the closing door opens it again, and it retries two seconds later.
 * A passenger calls the lift and blocks the door whenever they like; the controller still waits three seconds before it starts the motor.
 * The cases ask whether the lift can move, whether the door is closed whenever the motor runs, and whether the controller can set off with the door open.
 */

private import SI::s;

item def CallSignal;
item def ObstacleSignal;
item def OpenDoor;
item def CloseDoor;
item def StartMotor;
item def StopMotor;

port def CallButton {
    out item call : CallSignal;
}

port def DoorSensor {
    out item obstacle : ObstacleSignal;
}

port def DoorCommands {
    out item open : OpenDoor;
    out item close : CloseDoor;
}

port def MotorCommands {
    out item start : StartMotor;
    out item stop : StopMotor;
}

part def Door {
    port sensor : ~DoorSensor;
    port controller : ~DoorCommands;

    exhibit state Behaviour {
        transition first start then Open;

        state Open;
        state Closing;
        state Reopened;
        state Closed;

        transition first Open accept : CloseDoor via controller then Closing;
        transition first Closing accept after 2 [s] then Closed;
        transition first Closing accept : ObstacleSignal via sensor then Reopened;
        transition first Reopened accept after 2 [s] then Closing;
        transition first Closed accept : OpenDoor via controller then Open;
    }
}

part def Motor {
    port controller : ~MotorCommands;

    exhibit state Behaviour {
        transition first start then Stopped;

        state Stopped;
        state Running;

        transition first Stopped accept : StartMotor via controller then Running;
        transition first Running accept : StopMotor via controller then Stopped;
    }
}

part def Controller {
    port button : ~CallButton;
    port door : DoorCommands;
    port motor : MotorCommands;

    exhibit state Behaviour {
        transition first start then Idle;

        state Idle;
        state ClosingDoor;
        state Moving;

        transition first Idle accept : CallSignal via button do action { send new CloseDoor() via door; } then ClosingDoor;
        transition first ClosingDoor accept after 3 [s] do action { send new StartMotor() via motor; } then Moving;
        transition first Moving accept after 3 [s] do action { send new StopMotor() via motor; send new OpenDoor() via door; } then Idle;
    }
}

part def Lift {
    port button : ~CallButton;
    port doorSensor : ~DoorSensor;

    part controller : Controller;
    part door : Door;
    part motor : Motor;

    bind button = controller.button;
    flow from controller.door to door.controller;

    bind doorSensor = door.sensor;
    flow from controller.motor to motor.controller;
}

verification def DoorClosedWhileMoving {
    subject lift : Lift;

    objective {
        verify requirement {
            require constraint {
                mustAlways(not isStateActive(lift.motor.Behaviour.Running) or isStateActive(lift.door.Behaviour.Closed))
            }
        }
    }
}

verification def LiftCanMove {
    subject lift : Lift;

    objective {
        verify requirement {
            require constraint {
                eventually(isStateActive(lift.motor.Behaviour.Running))
            }
        }
    }
}

verification def CanSetOffWithDoorOpen {
    subject lift : Lift;

    objective {
        verify requirement {
            require constraint {
                eventually(isStateActive(lift.controller.Behaviour.Moving) and not isStateActive(lift.door.Behaviour.Closed))
            }
        }
    }
}
