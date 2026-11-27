package LampLitOnlyWhileSwitchOnViolatedTrace {
    private import Occurrences::HappensJustBefore;
    private import Time::DurationOf;
    private import SI::s;
    private import VerificationCases::VerdictKind;

    verification witness : LampLitOnlyWhileSwitchOnViolated {
        subject :>> system : Run;
        return :>> verdict = VerdictKind::fail;
    }

    part def Run :> Room {
        bind init.startShot = self.startShot;

        timeslice init [1] {
            assert constraint { DurationOf(init) == 0 [s] }
            event switch.Behaviour.start [1];
            event lamp.Behaviour.start [1];
        }
        succession : HappensJustBefore [1] first init then step1;
        timeslice step1 [1] {
            assert constraint { DurationOf(step1) == 0 [s] }
            event switch.Behaviour.turnOn1 [1];
        }
        succession : HappensJustBefore [1] first step1 then step2;
        timeslice step2 [1] {
            assert constraint { DurationOf(step2) == 0 [s] }
            event switch.Behaviour.turnOff1 [1];
            event lamp.Behaviour.lightUp1 [1];
        }

        part switch :>> switch {
            exhibit state Behaviour :>> Behaviour [1] {
                state off1 [1] :> Off;
                transition turnOn1 [1] :> turnOn
                    first off1
                    accept Press via button
                    do action {
                        action sendToggle1 [1] send new Toggle() via output;
                    }
                    then on1;
                state on1 [1] :> On;
                transition turnOff1 [1] :> turnOff
                    first on1
                    accept Press via button
                    do action {
                        action sendToggle1 [1] send new Toggle() via output;
                    }
                    then off2;
                state off2 [1] :> Off;
            }
        }

        part lamp :>> lamp {
            exhibit state Behaviour :>> Behaviour [1] {
                state dark1 [1] :> Dark;
                transition lightUp1 [1] :> lightUp
                    first dark1
                    accept Toggle via input
                    then lit1;
                state lit1 [1] :> Lit;
            }
        }
    }
}
