private import Occurrences::HappensDuring;
private import Occurrences::HappensJustBefore;
private import Occurrences::HappensWhile;
private import SI::s;
private import Time::DurationOf;

part def TrafficLightABehaviorNormalGreenUnreachableViolatedTrace :> CrossroadSystem {
    part controller :>> controller {
        exhibit state Behaviour :>> Behaviour {
            state operating1 :> Operating {
                state bothYellow1 :> BothYellow;
                action toBothRed1 :> stateTransitions {
                    action sendToggleA1 send new Toggle() via controlA;
                    action sendToggleB1 send new Toggle() via controlB;
                }
                state bothRed1 :> BothRed;
                action toTrafficOnA1 :> stateTransitions {
                    action sendToggleA2 send new Toggle() via controlA;
                }
                state trafficOnA1 :> TrafficOnA;
            }
        }
    }

    part trafficLightA :>> trafficLightA {
        action startup {
            action sendYellow1 send new YellowL() via lightControl;
        }

        exhibit state Behaviour :>> Behaviour {
            state normal1 :> Normal {
                state yellow1 :> Yellow;
                action toRed1 :> stateTransitions {
                    action sendRed1 send new RedL() via lightControl;
                }
                state red1 :> Red;
                action toGreen1 :> stateTransitions {
                    action sendGreen1 send new GreenL() via lightControl;
                }
                state green1 :> Green;
            }
        }
    }

    part trafficLightB :>> trafficLightB {
        action startup {
            action sendYellow1 send new YellowL() via lightControl;
        }

        exhibit state Behaviour :>> Behaviour {
            state normal1 :> Normal {
                state yellow1 :> Yellow;
                action toRed1 :> stateTransitions {
                    action sendRed1 send new RedL() via lightControl;
                }
                state red1 :> Red;
            }
        }
    }

    // stepN is the reaction of witness step N, dwellN the time advance that ends it
    snapshot step0;
    timeslice dwell1 { assert constraint { DurationOf(dwell1) == 1 [s] } }
    snapshot step2;
    timeslice dwell2 { assert constraint { DurationOf(dwell2) == 1 [s] } }
    snapshot step3;
    timeslice dwell3 { assert constraint { DurationOf(dwell3) == 1 [s] } }
    snapshot step4;
    timeslice dwell4 { assert constraint { DurationOf(dwell4) == 1 [s] } }
    snapshot step5;
    timeslice dwell5 { assert constraint { DurationOf(dwell5) == 1 [s] } }

    connection : HappensWhile connect step0 to self.startShot;
    succession : HappensJustBefore first step0 then dwell1;
    succession : HappensJustBefore first dwell1 then step2;
    succession : HappensJustBefore first step2 then dwell2;
    succession : HappensJustBefore first dwell2 then step3;
    succession : HappensJustBefore first step3 then dwell3;
    succession : HappensJustBefore first dwell3 then step4;
    succession : HappensJustBefore first step4 then dwell4;
    succession : HappensJustBefore first dwell4 then step5;
    succession : HappensJustBefore first step5 then dwell5;

    connection : HappensWhile connect step0 to trafficLightA.startup;
    connection : HappensWhile connect step0 to trafficLightB.startup;
    connection : HappensWhile connect step2 to controller.Behaviour.operating1.toBothRed1;
    connection : HappensWhile connect step3 to trafficLightA.Behaviour.normal1.toRed1;
    connection : HappensWhile connect step3 to trafficLightB.Behaviour.normal1.toRed1;
    connection : HappensWhile connect step4 to controller.Behaviour.operating1.toTrafficOnA1;
    connection : HappensWhile connect step5 to trafficLightA.Behaviour.normal1.toGreen1;

    connection : HappensDuring connect dwell1 to controller.Behaviour.operating1.bothYellow1;
    connection : HappensDuring connect dwell1 to trafficLightA.Behaviour.normal1.yellow1;
    connection : HappensDuring connect dwell1 to trafficLightB.Behaviour.normal1.yellow1;
    connection : HappensDuring connect dwell2 to controller.Behaviour.operating1.bothRed1;
    connection : HappensDuring connect dwell2 to trafficLightA.Behaviour.normal1.yellow1;
    connection : HappensDuring connect dwell2 to trafficLightB.Behaviour.normal1.yellow1;
    connection : HappensDuring connect dwell3 to controller.Behaviour.operating1.bothRed1;
    connection : HappensDuring connect dwell3 to trafficLightA.Behaviour.normal1.red1;
    connection : HappensDuring connect dwell3 to trafficLightB.Behaviour.normal1.red1;
    connection : HappensDuring connect dwell4 to controller.Behaviour.operating1.trafficOnA1;
    connection : HappensDuring connect dwell4 to trafficLightA.Behaviour.normal1.red1;
    connection : HappensDuring connect dwell4 to trafficLightB.Behaviour.normal1.red1;
    connection : HappensDuring connect dwell5 to controller.Behaviour.operating1.trafficOnA1;
    connection : HappensDuring connect dwell5 to trafficLightA.Behaviour.normal1.green1;
    connection : HappensDuring connect dwell5 to trafficLightB.Behaviour.normal1.red1;
}
