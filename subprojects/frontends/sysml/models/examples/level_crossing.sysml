private import ScalarValues::Integer;

item def ApproachSignal;
item def NearSignal;
item def EnterSignal;
item def LeaveSignal;
item def TrainApproaching;
item def TrainLeft;
item def Lower;
item def Lift;

port def Sensors {
    out item approach : ApproachSignal;
    out item near : NearSignal;
    out item enter : EnterSignal;
    out item leave : LeaveSignal;
}

port def TrackReports {
    out item trainApproaching : TrainApproaching;
    out item trainLeft : TrainLeft;
}

port def BarrierCommands {
    out item lower : Lower;
    out item lift : Lift;
}

part def Track {
    port sensors : ~Sensors;
    port controller : TrackReports;

    exhibit state Behaviour {
        transition first start then Free;

        state Free;
        state Approaching;
        state Near;
        state InCrossing;

        transition first Free accept : ApproachSignal via sensors do action { send new TrainApproaching() via controller; } then Approaching;
        transition first Approaching accept : NearSignal via sensors then Near;
        transition first Near accept : EnterSignal via sensors then InCrossing;
        transition first InCrossing accept : LeaveSignal via sensors do action { send new TrainLeft() via controller; } then Free;
    }
}

part def Controller {
    port trackA : ~TrackReports;
    port trackB : ~TrackReports;
    port barrier : BarrierCommands;

    exhibit state Behaviour {
        transition first start then Idle;

        state Idle;
        state TrainPresent;

        transition first Idle accept : TrainApproaching via trackA do action { send new Lower() via barrier; } then TrainPresent;
        transition first Idle accept : TrainApproaching via trackB do action { send new Lower() via barrier; } then TrainPresent;
        transition first TrainPresent accept : TrainLeft via trackA do action { send new Lift() via barrier; } then Idle;
        transition first TrainPresent accept : TrainLeft via trackB do action { send new Lift() via barrier; } then Idle;
    }
}

part def Barrier {
    port controller : ~BarrierCommands;

    exhibit state Behaviour {
        transition first start then Up;

        state Up;
        state Down;

        transition first Up accept : Lower via controller then Down;
        transition first Down accept : Lift via controller then Up;
    }
}

part def LevelCrossing {
    port trackASensors : ~Sensors;
    port trackBSensors : ~Sensors;

    part trackA : Track;
    part trackB : Track;
    part controller : Controller;
    part barrier : Barrier;

    bind trackASensors = trackA.sensors;
    flow from trackA.controller to controller.trackA;

    bind trackBSensors = trackB.sensors;
    flow from trackB.controller to controller.trackB;

    flow from controller.barrier to barrier.controller;
}

verification def BarrierDownWhileTrainInCrossingViolated {
    subject system : LevelCrossing;

    objective {
        verify requirement {
            require constraint {
                mustAlways(
                    not (isStateActive(system.trackA.Behaviour.InCrossing) or isStateActive(system.trackB.Behaviour.InCrossing))
                    or isStateActive(system.barrier.Behaviour.Down)
                )
            }
        }
    }
}

verification def TwoTrainsCanBeInTheCrossing {
    subject system : LevelCrossing;

    objective {
        verify requirement {
            require constraint {
                eventually(
                    isStateActive(system.trackA.Behaviour.InCrossing)
                    and isStateActive(system.trackB.Behaviour.InCrossing)
                )
            }
        }
    }
}
