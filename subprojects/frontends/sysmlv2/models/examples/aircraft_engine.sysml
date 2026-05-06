private import ScalarValues::Boolean;

item def PowerOnSignal;
item def StartSignal;
item def IgnitionSuccessSignal;
item def FailureSignal;
item def WarmupCompleteSignal;
item def IdleCommand;
item def ResumeCommand;
item def OverheatSignal;
item def JFSFailureSignal;
item def ShutdownCommand;
item def CriticalFailureSignal;
item def PerformanceDegradation;
item def StabilizationSignal;
item def RestartCommand;
item def RestartSuccessSignal;
item def RestartFailureSignal;
item def IrrecoverableSignal;
item def MaintenanceStartSignal;
item def CompleteSignal;

port def Control {
    in item powerOnSignal : PowerOnSignal;
    in item startSignal : StartSignal;
    in item ignitionSuccessSignal : IgnitionSuccessSignal;
    in item failureSignal : FailureSignal;
    in item warmupCompleteSignal : WarmupCompleteSignal;
    in item idleCommand : IdleCommand;
    in item resumeCommand : ResumeCommand;
    in item overheatSignal : OverheatSignal;
    in item jFSFailureSignal : JFSFailureSignal;
    in item shutdownCommand : ShutdownCommand;
    in item criticalFailureSignal : CriticalFailureSignal;
    in item performanceDegradation : PerformanceDegradation;
    in item stabilizationSignal : StabilizationSignal;
    in item restartCommand : RestartCommand;
    in item restartSuccessSignal : RestartSuccessSignal;
    in item restartFailureSignal : RestartFailureSignal;
    in item irrecoverableSignal : IrrecoverableSignal;
    in item maintenanceStartSignal : MaintenanceStartSignal;
    in item completeSignal : CompleteSignal;
}

part def EngineStateMachine {
    attribute preStartupCheckOk: Boolean default true;
    port control: Control;

    exhibit state Behaviour {
        state Off;
        state PoweringUp;
        state Startup;
        state Warmup;
        state Nominal;
        state Idle;
        state Overheat;
        state JFS_Failure;
        state Restart;
        state Maintenance;
        state Shutdown;
        state EmergencyShutdown;
        state EngineDown;

        transition first start then Off;

        transition first Off
            accept : PowerOnSignal via control
            then PoweringUp;

        transition first PoweringUp
            accept : StartSignal via control
            if (preStartupCheckOk == true)
            then Startup;

        transition first Startup
            accept : IgnitionSuccessSignal via control
            then Warmup;

        transition first Startup
            accept : FailureSignal via control
            then JFS_Failure;

        transition first Warmup
            accept : WarmupCompleteSignal via control
            then Nominal;

        transition first Nominal
            accept : IdleCommand via control
            then Idle;

        transition first Nominal
            accept : OverheatSignal via control
            then Overheat;

        transition first Nominal
            accept : JFSFailureSignal via control
            then JFS_Failure;

        transition first Nominal
            accept : ShutdownCommand via control
            then Shutdown;

        transition first Nominal
            accept : CriticalFailureSignal via control
            then EmergencyShutdown;

        transition first Nominal
            accept : PerformanceDegradation via control
            then EngineDown;

        transition first Idle
            accept : ResumeCommand via control
            then Nominal;

        transition first Overheat
            accept : StabilizationSignal via control
            then Nominal;

        transition first JFS_Failure
            accept : RestartCommand via control
            then Restart;

        transition first JFS_Failure
            accept : IrrecoverableSignal via control
            then EngineDown;

        transition first Restart
            accept : RestartSuccessSignal via control
            then Warmup;

        transition first Restart
            accept : RestartFailureSignal via control
            then EngineDown;

        transition first Maintenance
            accept : MaintenanceStartSignal via control
            then Off;

        transition first Shutdown
            accept : CompleteSignal via control
            then Off;

        transition first EmergencyShutdown
            accept : CompleteSignal via control
            then Off;
    }
}

verification def OffReachable {
    subject system : EngineStateMachine;

    objective {
        verify requirement {
            require constraint {
                eventually(
                    isStateActive(system.Behaviour.Off)
                )
            }
        }
    }
}

verification def NominalReachable {
    subject system : EngineStateMachine;

    objective {
        verify requirement {
            require constraint {
                eventually(
                    isStateActive(system.Behaviour.Nominal)
                )
            }
        }
    }
}

verification def JFS_FailureReachable {
    subject system : EngineStateMachine;

    objective {
        verify requirement {
            require constraint {
                eventually(
                    isStateActive(system.Behaviour.JFS_Failure)
                )
            }
        }
    }
}

verification def EngineDownReachable {
    subject system : EngineStateMachine;

    objective {
        verify requirement {
            require constraint {
                eventually(
                    isStateActive(system.Behaviour.EngineDown)
                )
            }
        }
    }
}

verification def MaintenanceUnreachable {
    subject system : EngineStateMachine;

    objective {
        verify requirement {
            require constraint {
                not eventually(
                    isStateActive(system.Behaviour.Maintenance)
                )
            }
        }
    }
}

verification def NominalAndIdleNeverSimultaneous {
    subject system : EngineStateMachine;

    objective {
        verify requirement {
            require constraint {
                mustAlways(not(
                    isStateActive(system.Behaviour.Nominal)
                    and isStateActive(system.Behaviour.Idle)
                ))
            }
        }
    }
}
