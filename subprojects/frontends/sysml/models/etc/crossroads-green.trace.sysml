package TrafficLightABehaviorNormalGreenUnreachableViolatedTrace {
    private import Occurrences::HappensJustBefore;
    private import Time::DurationOf;
    private import SI::s;
    private import VerificationCases::VerdictKind;

    verification witness : TrafficLightABehaviorNormalGreenUnreachableViolated {
        subject :>> system : Run;
        return :>> verdict = VerdictKind::fail;
    }

    part def Run :> CrossroadSystem {
        bind init.startShot = self.startShot;

        timeslice init [1] {
            assert constraint { DurationOf(init) == 0 [s] }
            event controller.Behaviour.start [1];
            event trafficLightA.Behaviour.start [1];
            event trafficLightB.Behaviour.start [1];
        }
        succession : HappensJustBefore [1] first init then dwell1;
        timeslice dwell1 [1] {
            assert constraint { DurationOf(dwell1) == 1 [s] }
        }
        succession : HappensJustBefore [1] first dwell1 then step1;
        timeslice step1 [1] {
            assert constraint { DurationOf(step1) == 0 [s] }
            event controller.Behaviour.operating1.bothYellowToBothRed1 [1];
        }
        succession : HappensJustBefore [1] first step1 then dwell2;
        timeslice dwell2 [1] {
            assert constraint { DurationOf(dwell2) == 2 [s] }
        }
        succession : HappensJustBefore [1] first dwell2 then step2;
        timeslice step2 [1] {
            assert constraint { DurationOf(step2) == 0 [s] }
            event controller.Behaviour.operating1.bothRedToTrafficOnA1 [1];
            event trafficLightA.Behaviour.normal1.yellowToRed1 [1];
            event trafficLightB.Behaviour.normal1.yellowToRed1 [1];
        }
        succession : HappensJustBefore [1] first step2 then step3;
        timeslice step3 [1] {
            assert constraint { DurationOf(step3) == 0 [s] }
            event trafficLightA.Behaviour.normal1.redToGreen1 [1];
        }

        part controller :>> controller {
            exhibit state Behaviour :>> Behaviour [1] {
                state operating1 [1] :> Operating {
                    state bothYellow1 [1] :> BothYellow {
                        event occurrence firstDwell [1] ::> dwell1;
                        event occurrence lastDwell [1] ::> dwell1;
                    }
                    transition bothYellowToBothRed1 [1]
                        first bothYellow1
                        accept after 1[s]
                        do action {
                            action sendToggle1 [1] send new Toggle() via controlA;
                            action sendToggle2 [1] send new Toggle() via controlB;
                        }
                        then bothRed1;
                    state bothRed1 [1] :> BothRed {
                        event occurrence firstDwell [1] ::> dwell2;
                        event occurrence lastDwell [1] ::> dwell2;
                    }
                    transition bothRedToTrafficOnA1 [1]
                        first bothRed1
                        accept after 2[s]
                        do action {
                            action sendToggle1 [1] send new Toggle() via controlA;
                        }
                        then trafficOnA1;
                    state trafficOnA1 [1] :> TrafficOnA;
                }
            }
        }

        part trafficLightA :>> trafficLightA {
            exhibit state Behaviour :>> Behaviour [1] {
                state normal1 [1] :> Normal {
                    state yellow1 [1] :> Yellow {
                        entry action sendYellowL1 send new YellowL() via lightControl;
                        event occurrence firstDwell [1] ::> dwell1;
                        event occurrence lastDwell [1] ::> dwell2;
                    }
                    transition yellowToRed1 [1]
                        first yellow1
                        accept Toggle via control
                        then red1;
                    state red1 [1] :> Red {
                        entry action sendRedL1 send new RedL() via lightControl;
                    }
                    transition redToGreen1 [1]
                        first red1
                        accept Toggle via control
                        then green1;
                    state green1 [1] :> Green {
                        entry action sendGreenL1 send new GreenL() via lightControl;
                    }
                }
            }
        }

        part trafficLightB :>> trafficLightB {
            exhibit state Behaviour :>> Behaviour [1] {
                state normal1 [1] :> Normal {
                    state yellow1 [1] :> Yellow {
                        entry action sendYellowL1 send new YellowL() via lightControl;
                        event occurrence firstDwell [1] ::> dwell1;
                        event occurrence lastDwell [1] ::> dwell2;
                    }
                    transition yellowToRed1 [1]
                        first yellow1
                        accept Toggle via control
                        then red1;
                    state red1 [1] :> Red {
                        entry action sendRedL1 send new RedL() via lightControl;
                    }
                }
            }
        }
    }
}
