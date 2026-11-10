const sources = {
  sysmlNaive: String.raw`private import ScalarValues::Integer;

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

verification def BarrierDownWhileTrainInCrossing {
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
`,
  sysmlFixed: String.raw`private import ScalarValues::Integer;

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
    attribute trains : Integer default 0;

    port trackA : ~TrackReports;
    port trackB : ~TrackReports;
    port barrier : BarrierCommands;

    exhibit state Behaviour parallel {
        state TrackA {
            transition first start then Watching;

            state Watching;

            transition first Watching accept : TrainApproaching via trackA if trains == 0 do action { assign trains := 1; send new Lower() via barrier; } then Watching;
            transition first Watching accept : TrainApproaching via trackA if trains > 0 do action { assign trains := trains + 1; } then Watching;
            transition first Watching accept : TrainLeft via trackA if trains == 1 do action { assign trains := 0; send new Lift() via barrier; } then Watching;
            transition first Watching accept : TrainLeft via trackA if trains > 1 do action { assign trains := trains - 1; } then Watching;
        }

        state TrackB {
            transition first start then Watching;

            state Watching;

            transition first Watching accept : TrainApproaching via trackB if trains == 0 do action { assign trains := 1; send new Lower() via barrier; } then Watching;
            transition first Watching accept : TrainApproaching via trackB if trains > 0 do action { assign trains := trains + 1; } then Watching;
            transition first Watching accept : TrainLeft via trackB if trains == 1 do action { assign trains := 0; send new Lift() via barrier; } then Watching;
            transition first Watching accept : TrainLeft via trackB if trains > 1 do action { assign trains := trains - 1; } then Watching;
        }
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

verification def BarrierDownWhileTrainInCrossing {
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
`,
  gammaNaive: String.raw`package LevelCrossingNaive

interface Sensors {
    out event approach
    out event near
    out event enter
    out event leave
}

interface TrackReports {
    out event trainApproaching
    out event trainLeft
}

interface BarrierCommands {
    out event lower
    out event lift
}

statechart Track [
    port sensors : requires Sensors,
    port controller : provides TrackReports
] {
    region Main {
        entry transition to Free

        state Free
        state Approaching
        state Near
        state InCrossing

        transition Free to Approaching when event sensors.approach / raise controller.trainApproaching;
        transition Approaching to Near when event sensors.near
        transition Near to InCrossing when event sensors.enter
        transition InCrossing to Free when event sensors.leave / raise controller.trainLeft;
    }
}

statechart Controller [
    port trackA : requires TrackReports,
    port trackB : requires TrackReports,
    port barrier : provides BarrierCommands
] {
    region Main {
        entry transition to Idle

        state Idle
        state TrainPresent

        transition Idle to TrainPresent when event trackA.trainApproaching / raise barrier.lower;
        transition Idle to TrainPresent when event trackB.trainApproaching / raise barrier.lower;
        transition TrainPresent to Idle when event trackA.trainLeft / raise barrier.lift;
        transition TrainPresent to Idle when event trackB.trainLeft / raise barrier.lift;
    }
}

statechart Barrier [
    port controller : requires BarrierCommands
] {
    region Main {
        entry transition to Up

        state Up
        state Down

        transition Up to Down when event controller.lower
        transition Down to Up when event controller.lift
    }
}

sync component LevelCrossing [
    port trackASensors : requires Sensors,
    port trackBSensors : requires Sensors
] {
    component trackA : Track
    component trackB : Track
    component controller : Controller
    component barrier : Barrier

    bind trackASensors -> trackA.sensors
    bind trackBSensors -> trackB.sensors

    channel [ trackA.controller ] -> [ controller.trackA ]
    channel [ trackB.controller ] -> [ controller.trackB ]
    channel [ controller.barrier ] -> [ barrier.controller ]
}

verification case BarrierDownWhileTrainInCrossing {
    component system : LevelCrossing
    property {
        AG ((!isActive (system.trackA.Main.InCrossing) && !isActive (system.trackB.Main.InCrossing)) || isActive (system.barrier.Main.Down))
    }
}

verification case TwoTrainsCanBeInTheCrossing {
    component system : LevelCrossing
    property {
        EF (isActive (system.trackA.Main.InCrossing) && isActive (system.trackB.Main.InCrossing))
    }
}
`,
  oxstsNaive: String.raw`package example::levelcrossing::naive

enum TrackState {
    Free,
    Approaching,
    Near,
    InCrossing,
}

enum BarrierState {
    Up,
    Down,
}

class Track {
    var state: TrackState := TrackState::Free
    var trainApproaching: bool := false
    var trainLeft: bool := false

    @Trace
    tran move(from: TrackState, to: TrackState) {
        assume (state == from)
        state := to
    }

    redefine tran {
        trainApproaching := false
        trainLeft := false
        choice {
            inline move(TrackState::Free, TrackState::Approaching)
            trainApproaching := true
        } or {
            inline move(TrackState::Approaching, TrackState::Near)
        } or {
            inline move(TrackState::Near, TrackState::InCrossing)
        } or {
            inline move(TrackState::InCrossing, TrackState::Free)
            trainLeft := true
        } or {
            assume (true)
        }
    }
}

enum ControllerState {
    Idle,
    TrainPresent,
}

class Controller {
    var state: ControllerState := ControllerState::Idle
    var lower: bool := false
    var lift: bool := false

    @Trace
    tran step(trackA: Track, trackB: Track) {
        lower := state == ControllerState::Idle && (trackA.trainApproaching || trackB.trainApproaching)
        lift := state == ControllerState::TrainPresent && (trackA.trainLeft || trackB.trainLeft)
        if (lower || lift) {
            state := if lower then ControllerState::TrainPresent else ControllerState::Idle
        }
    }
}

class Barrier {
    var state: BarrierState := BarrierState::Up

    @Trace
    tran step(lower: bool, lift: bool) {
        if (lower || lift) {
            state := if lower then BarrierState::Down else BarrierState::Up
        }
    }
}

class LevelCrossing {
    contains trackA: Track
    contains trackB: Track
    contains controller: Controller
    contains barrier: Barrier

    @Trace
    redefine tran {
        inline barrier.step(controller.lower, controller.lift)
        inline controller.step(trackA, trackB)
        inline trackA.main()
        inline trackB.main()
    }
}

@VerificationCase
class BarrierDownWhileTrainInCrossing : LevelCrossing {
    prop {
        return AG (!(trackA.state == TrackState::InCrossing || trackB.state == TrackState::InCrossing) || barrier.state == BarrierState::Down)
    }
}

@VerificationCase
class TwoTrainsCanBeInTheCrossing : LevelCrossing {
    prop {
        return EF (trackA.state == TrackState::InCrossing && trackB.state == TrackState::InCrossing)
    }
}
`,
  sysmlTrace: String.raw`private import Occurrences::HappensDuring;
private import Occurrences::HappensJustBefore;
private import Occurrences::HappensWhile;
private import SI::s;
private import Time::DurationOf;

part def BarrierDownWhileTrainInCrossingTrace :> LevelCrossing {
    part trackA :>> trackA {
        exhibit state Behaviour :>> Behaviour {
            state free1 :> Free;
            action toApproaching1 :> stateTransitions {
                action sendTrainApproaching1 send new TrainApproaching() via controller;
            }
            state approaching1 :> Approaching;
            action toNear1 :> stateTransitions;
            state near1 :> Near;
            action toInCrossing1 :> stateTransitions;
            state inCrossing1 :> InCrossing;
        }
    }

    part trackB :>> trackB {
        exhibit state Behaviour :>> Behaviour {
            state free1 :> Free;
            action toApproaching1 :> stateTransitions {
                action sendTrainApproaching1 send new TrainApproaching() via controller;
            }
            state approaching1 :> Approaching;
            action toNear1 :> stateTransitions;
            state near1 :> Near;
            action toInCrossing1 :> stateTransitions;
            state inCrossing1 :> InCrossing;
            action toFree1 :> stateTransitions {
                action sendTrainLeft1 send new TrainLeft() via controller;
            }
            state free2 :> Free;
        }
    }

    part controller :>> controller {
        exhibit state Behaviour :>> Behaviour {
            state idle1 :> Idle;
            action toTrainPresent1 :> stateTransitions {
                action sendLower1 send new Lower() via barrier;
            }
            state trainPresent1 :> TrainPresent;
            action toIdle1 :> stateTransitions {
                action sendLift1 send new Lift() via barrier;
            }
            state idle2 :> Idle;
        }
    }

    part barrier :>> barrier {
        exhibit state Behaviour :>> Behaviour {
            state up1 :> Up;
            action toDown1 :> stateTransitions;
            state down1 :> Down;
            action toUp1 :> stateTransitions;
            state up2 :> Up;
        }
    }

    snapshot step0;
    snapshot step1;
    timeslice dwell1 {
        assert constraint { DurationOf(dwell1) == 1 [s] }
    }
    snapshot step2;
    timeslice dwell2 {
        assert constraint { DurationOf(dwell2) == 1 [s] }
    }
    snapshot step3;
    timeslice dwell3 {
        assert constraint { DurationOf(dwell3) == 1 [s] }
    }
    snapshot step4;
    timeslice dwell4 {
        assert constraint { DurationOf(dwell4) == 1 [s] }
    }
    snapshot step5;
    timeslice dwell5 {
        assert constraint { DurationOf(dwell5) == 1 [s] }
    }
    snapshot step6;
    timeslice dwell6 {
        assert constraint { DurationOf(dwell6) == 1 [s] }
    }

    connection : HappensWhile connect step0 to self.startShot;
    succession : HappensJustBefore first step0 then step1;
    succession : HappensJustBefore first step1 then dwell1;
    succession : HappensJustBefore first dwell1 then step2;
    succession : HappensJustBefore first step2 then dwell2;
    succession : HappensJustBefore first dwell2 then step3;
    succession : HappensJustBefore first step3 then dwell3;
    succession : HappensJustBefore first dwell3 then step4;
    succession : HappensJustBefore first step4 then dwell4;
    succession : HappensJustBefore first dwell4 then step5;
    succession : HappensJustBefore first step5 then dwell5;
    succession : HappensJustBefore first dwell5 then step6;
    succession : HappensJustBefore first step6 then dwell6;

    connection : HappensWhile connect step1 to trackB.Behaviour.toApproaching1;
    connection : HappensWhile connect step2 to trackB.Behaviour.toNear1;
    connection : HappensWhile connect step2 to controller.Behaviour.toTrainPresent1;
    connection : HappensWhile connect step3 to trackB.Behaviour.toInCrossing1;
    connection : HappensWhile connect step3 to barrier.Behaviour.toDown1;
    connection : HappensWhile connect step4 to trackA.Behaviour.toApproaching1;
    connection : HappensWhile connect step4 to trackB.Behaviour.toFree1;
    connection : HappensWhile connect step5 to trackA.Behaviour.toNear1;
    connection : HappensWhile connect step5 to controller.Behaviour.toIdle1;
    connection : HappensWhile connect step6 to trackA.Behaviour.toInCrossing1;
    connection : HappensWhile connect step6 to barrier.Behaviour.toUp1;

    connection : HappensDuring connect dwell1 to trackA.Behaviour.free1;
    connection : HappensDuring connect dwell1 to trackB.Behaviour.approaching1;
    connection : HappensDuring connect dwell1 to controller.Behaviour.idle1;
    connection : HappensDuring connect dwell1 to barrier.Behaviour.up1;
    connection : HappensDuring connect dwell2 to trackA.Behaviour.free1;
    connection : HappensDuring connect dwell2 to trackB.Behaviour.near1;
    connection : HappensDuring connect dwell2 to controller.Behaviour.trainPresent1;
    connection : HappensDuring connect dwell2 to barrier.Behaviour.up1;
    connection : HappensDuring connect dwell3 to trackA.Behaviour.free1;
    connection : HappensDuring connect dwell3 to trackB.Behaviour.inCrossing1;
    connection : HappensDuring connect dwell3 to controller.Behaviour.trainPresent1;
    connection : HappensDuring connect dwell3 to barrier.Behaviour.down1;
    connection : HappensDuring connect dwell4 to trackA.Behaviour.approaching1;
    connection : HappensDuring connect dwell4 to trackB.Behaviour.free2;
    connection : HappensDuring connect dwell4 to controller.Behaviour.trainPresent1;
    connection : HappensDuring connect dwell4 to barrier.Behaviour.down1;
    connection : HappensDuring connect dwell5 to trackA.Behaviour.near1;
    connection : HappensDuring connect dwell5 to trackB.Behaviour.free2;
    connection : HappensDuring connect dwell5 to controller.Behaviour.idle2;
    connection : HappensDuring connect dwell5 to barrier.Behaviour.down1;
    connection : HappensDuring connect dwell6 to trackA.Behaviour.inCrossing1;
    connection : HappensDuring connect dwell6 to trackB.Behaviour.free2;
    connection : HappensDuring connect dwell6 to controller.Behaviour.idle2;
    connection : HappensDuring connect dwell6 to barrier.Behaviour.up2;
}
`,
  sysmlTraceReachable: String.raw`private import Occurrences::HappensDuring;
private import Occurrences::HappensJustBefore;
private import Occurrences::HappensWhile;
private import SI::s;
private import Time::DurationOf;

part def TwoTrainsCanBeInTheCrossingTrace :> LevelCrossing {
    part trackA :>> trackA {
        exhibit state Behaviour :>> Behaviour {
            state free1 :> Free;
            action toApproaching1 :> stateTransitions {
                action sendTrainApproaching1 send new TrainApproaching() via controller;
            }
            state approaching1 :> Approaching;
            action toNear1 :> stateTransitions;
            state near1 :> Near;
            action toInCrossing1 :> stateTransitions;
            state inCrossing1 :> InCrossing;
        }
    }

    part trackB :>> trackB {
        exhibit state Behaviour :>> Behaviour {
            state free1 :> Free;
            action toApproaching1 :> stateTransitions {
                action sendTrainApproaching1 send new TrainApproaching() via controller;
            }
            state approaching1 :> Approaching;
            action toNear1 :> stateTransitions;
            state near1 :> Near;
            action toInCrossing1 :> stateTransitions;
            state inCrossing1 :> InCrossing;
        }
    }

    part controller :>> controller {
        exhibit state Behaviour :>> Behaviour {
            state idle1 :> Idle;
            action toTrainPresent1 :> stateTransitions {
                action sendLower1 send new Lower() via barrier;
            }
            state trainPresent1 :> TrainPresent;
        }
    }

    part barrier :>> barrier {
        exhibit state Behaviour :>> Behaviour {
            state up1 :> Up;
            action toDown1 :> stateTransitions;
            state down1 :> Down;
        }
    }

    snapshot step0;
    snapshot step1;
    timeslice dwell1 {
        assert constraint { DurationOf(dwell1) == 1 [s] }
    }
    snapshot step2;
    timeslice dwell2 {
        assert constraint { DurationOf(dwell2) == 1 [s] }
    }
    snapshot step3;
    timeslice dwell3 {
        assert constraint { DurationOf(dwell3) == 1 [s] }
    }

    connection : HappensWhile connect step0 to self.startShot;
    succession : HappensJustBefore first step0 then step1;
    succession : HappensJustBefore first step1 then dwell1;
    succession : HappensJustBefore first dwell1 then step2;
    succession : HappensJustBefore first step2 then dwell2;
    succession : HappensJustBefore first dwell2 then step3;
    succession : HappensJustBefore first step3 then dwell3;

    connection : HappensWhile connect step1 to trackA.Behaviour.toApproaching1;
    connection : HappensWhile connect step1 to trackB.Behaviour.toApproaching1;
    connection : HappensWhile connect step2 to trackA.Behaviour.toNear1;
    connection : HappensWhile connect step2 to trackB.Behaviour.toNear1;
    connection : HappensWhile connect step2 to controller.Behaviour.toTrainPresent1;
    connection : HappensWhile connect step3 to trackA.Behaviour.toInCrossing1;
    connection : HappensWhile connect step3 to trackB.Behaviour.toInCrossing1;
    connection : HappensWhile connect step3 to barrier.Behaviour.toDown1;

    connection : HappensDuring connect dwell1 to trackA.Behaviour.approaching1;
    connection : HappensDuring connect dwell1 to trackB.Behaviour.approaching1;
    connection : HappensDuring connect dwell1 to controller.Behaviour.idle1;
    connection : HappensDuring connect dwell1 to barrier.Behaviour.up1;
    connection : HappensDuring connect dwell2 to trackA.Behaviour.near1;
    connection : HappensDuring connect dwell2 to trackB.Behaviour.near1;
    connection : HappensDuring connect dwell2 to controller.Behaviour.trainPresent1;
    connection : HappensDuring connect dwell2 to barrier.Behaviour.up1;
    connection : HappensDuring connect dwell3 to trackA.Behaviour.inCrossing1;
    connection : HappensDuring connect dwell3 to trackB.Behaviour.inCrossing1;
    connection : HappensDuring connect dwell3 to controller.Behaviour.trainPresent1;
    connection : HappensDuring connect dwell3 to barrier.Behaviour.down1;
}
`,
};
