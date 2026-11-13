doc
/*
 * An aircraft's landing gear behind two bay doors, which a controller moves on the pilot's handle.
 * To retract the gear, the controller opens the doors, raises the gear and closes the doors; to extend it, it does the same the other way round.
 * The doors and the gear report when their sensors find a movement ended, and the pilot may move the handle back at any moment.
 * The cases ask whether the gear can be retracted and lowered again, and whether the gear ever moves while the doors are not fully open.
 */

item def HandleUp;
item def HandleDown;

item def OpenDoors;
item def CloseDoors;
item def DoorsFullyOpen;
item def DoorsFullyClosed;
item def DoorsAreOpen;
item def DoorsAreClosed;

item def Retract;
item def Extend;
item def UpLocked;
item def DownLocked;
item def GearIsUp;
item def GearIsDown;

port def Handle {
    out item up : HandleUp;
    out item down : HandleDown;
}

port def DoorCommands {
    out item open : OpenDoors;
    out item close : CloseDoors;
}

port def DoorSensors {
    out item fullyOpen : DoorsFullyOpen;
    out item fullyClosed : DoorsFullyClosed;
}

port def DoorReports {
    out item open : DoorsAreOpen;
    out item closed : DoorsAreClosed;
}

port def GearCommands {
    out item retract : Retract;
    out item extend : Extend;
}

port def GearSensors {
    out item upLocked : UpLocked;
    out item downLocked : DownLocked;
}

port def GearReports {
    out item up : GearIsUp;
    out item down : GearIsDown;
}

part def Doors {
    port commands : ~DoorCommands;
    port sensors : ~DoorSensors;
    port reports : DoorReports;

    exhibit state Behaviour {
        transition first start then Closed;

        state Closed;
        state Opening;
        state Open;
        state Closing;

        transition first Closed accept : OpenDoors via commands then Opening;
        transition first Opening accept : DoorsFullyOpen via sensors do action { send new DoorsAreOpen() via reports; } then Open;
        transition first Opening accept : CloseDoors via commands then Closing;
        transition first Open accept : CloseDoors via commands then Closing;
        transition first Closing accept : DoorsFullyClosed via sensors do action { send new DoorsAreClosed() via reports; } then Closed;
        transition first Closing accept : OpenDoors via commands then Opening;
    }
}

part def Gear {
    port commands : ~GearCommands;
    port sensors : ~GearSensors;
    port reports : GearReports;

    exhibit state Behaviour {
        transition first start then Down;

        state Down;
        state Retracting;
        state Up;
        state Extending;

        transition first Down accept : Retract via commands then Retracting;
        transition first Retracting accept : UpLocked via sensors do action { send new GearIsUp() via reports; } then Up;
        transition first Retracting accept : Extend via commands then Extending;
        transition first Up accept : Extend via commands then Extending;
        transition first Extending accept : DownLocked via sensors do action { send new GearIsDown() via reports; } then Down;
        transition first Extending accept : Retract via commands then Retracting;
    }
}

part def Controller {
    port handle : ~Handle;
    port doors : DoorCommands;
    port doorReports : ~DoorReports;
    port gear : GearCommands;
    port gearReports : ~GearReports;

    exhibit state Behaviour {
        transition first start then GearDown;

        state GearDown;
        state OpeningToRetract;
        state Retracting;
        state ClosingAfterRetract;
        state GearUp;
        state OpeningToExtend;
        state Extending;
        state ClosingAfterExtend;

        transition first GearDown accept : HandleUp via handle do action { send new OpenDoors() via doors; } then OpeningToRetract;

        transition first OpeningToRetract accept : DoorsAreOpen via doorReports do action { send new Retract() via gear; } then Retracting;
        transition first OpeningToRetract accept : HandleDown via handle do action { send new CloseDoors() via doors; } then ClosingAfterExtend;

        transition first Retracting accept : GearIsUp via gearReports do action { send new CloseDoors() via doors; } then ClosingAfterRetract;
        transition first Retracting accept : HandleDown via handle do action { send new Extend() via gear; } then Extending;

        transition first ClosingAfterRetract accept : DoorsAreClosed via doorReports then GearUp;
        transition first ClosingAfterRetract accept : HandleDown via handle do action { send new Extend() via gear; } then Extending;

        transition first GearUp accept : HandleDown via handle do action { send new OpenDoors() via doors; } then OpeningToExtend;

        transition first OpeningToExtend accept : DoorsAreOpen via doorReports do action { send new Extend() via gear; } then Extending;
        transition first OpeningToExtend accept : HandleUp via handle do action { send new CloseDoors() via doors; } then ClosingAfterRetract;

        transition first Extending accept : GearIsDown via gearReports do action { send new CloseDoors() via doors; } then ClosingAfterExtend;
        transition first Extending accept : HandleUp via handle do action { send new Retract() via gear; } then Retracting;

        transition first ClosingAfterExtend accept : DoorsAreClosed via doorReports then GearDown;
        transition first ClosingAfterExtend accept : HandleUp via handle do action { send new OpenDoors() via doors; } then OpeningToRetract;
    }
}

part def LandingGearSystem {
    port handle : ~Handle;
    port doorSensors : ~DoorSensors;
    port gearSensors : ~GearSensors;

    part controller : Controller;
    part doors : Doors;
    part gear : Gear;

    bind handle = controller.handle;

    flow from controller.doors to doors.commands;
    bind doorSensors = doors.sensors;
    flow from doors.reports to controller.doorReports;

    flow from controller.gear to gear.commands;
    bind gearSensors = gear.sensors;
    flow from gear.reports to controller.gearReports;
}

verification def GearCanBeRetracted {
    subject system : LandingGearSystem;

    objective {
        verify requirement {
            require constraint {
                eventually(
                    isStateActive(system.gear.Behaviour.Up)
                    and isStateActive(system.doors.Behaviour.Closed)
                )
            }
        }
    }
}

verification def GearCanBeLoweredAgain {
    subject system : LandingGearSystem;

    objective {
        verify requirement {
            require constraint {
                eventually(
                    isStateActive(system.controller.Behaviour.OpeningToExtend)
                )
            }
        }
    }
}

verification def BayClosedInCruise {
    subject system : LandingGearSystem;

    objective {
        verify requirement {
            require constraint {
                mustAlways(not(
                    isStateActive(system.controller.Behaviour.GearUp)
                    and not isStateActive(system.doors.Behaviour.Closed)
                ))
            }
        }
    }
}

verification def GearMovesOnlyWithDoorsOpen {
    subject system : LandingGearSystem;

    objective {
        verify requirement {
            require constraint {
                mustAlways(not(
                    (isStateActive(system.gear.Behaviour.Retracting) or isStateActive(system.gear.Behaviour.Extending))
                    and not isStateActive(system.doors.Behaviour.Open)
                ))
            }
        }
    }
}
