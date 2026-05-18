private import ScalarValues::Integer;

item def BatteryCriticalAlert;

port def AlertPort {
    in item batteryCritical : BatteryCriticalAlert;
}

part def PowerManager {
    attribute charge: Integer default 100;
    port alertPort: AlertPort;

    exhibit state Behaviour {
        state On {
            entry action {
                assign charge := charge - 1;
            }
        }
        state PowerCritical {
            entry action {
                assign charge := charge - 1;
            }
        }
        state Off;

        transition first start then On;

        transition first On
            accept : BatteryCriticalAlert via alertPort
            if charge < 20
            then PowerCritical;

        transition first On
            accept : BatteryCriticalAlert via alertPort
            if charge >= 20
            then On;

        transition first PowerCritical
            accept : BatteryCriticalAlert via alertPort
            if charge < 5
            then Off;

        transition first PowerCritical
            accept : BatteryCriticalAlert via alertPort
            if charge >= 5
            then PowerCritical;
    }
}

verification def OnReachable {
    subject system : PowerManager;

    objective {
        verify requirement {
            require constraint {
                eventually(
                    isStateActive(system.Behaviour.On)
                )
            }
        }
    }
}

verification def LowPowerReachable {
    subject system : PowerManager;

    objective {
        verify requirement {
            require constraint {
                eventually(
                    isStateActive(system.Behaviour.PowerCritical)
                )
            }
        }
    }
}

verification def OffReachable {
    subject system : PowerManager;

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

verification def OnAndLowPowerNeverSimultaneous {
    subject system : PowerManager;

    objective {
        verify requirement {
            require constraint {
                mustAlways(not(
                    isStateActive(system.Behaviour.On)
                    and isStateActive(system.Behaviour.PowerCritical)
                ))
            }
        }
    }
}

verification def OnAndOffNeverSimultaneous {
    subject system : PowerManager;

    objective {
        verify requirement {
            require constraint {
                mustAlways(not(
                    isStateActive(system.Behaviour.On)
                    and isStateActive(system.Behaviour.Off)
                ))
            }
        }
    }
}

verification def LowPowerAndOffNeverSimultaneous {
    subject system : PowerManager;

    objective {
        verify requirement {
            require constraint {
                mustAlways(not(
                    isStateActive(system.Behaviour.PowerCritical)
                    and isStateActive(system.Behaviour.Off)
                ))
            }
        }
    }
}
