private import ScalarValues::Integer;

item def BatteryLowAlert;

port def PowerPort {
    in item batteryLow : BatteryLowAlert;
}

part def PowerManager {
    attribute batteryLevel: Integer default 100;
    port powerPort: PowerPort;

    exhibit state Behaviour {
        state On {
            entry action {
                assign batteryLevel := batteryLevel - 1;
            }
        }
        state LowPower {
            entry action {
                assign batteryLevel := batteryLevel - 1;
            }
        }
        state Off;

        transition first start then On;

        transition first On
            accept : BatteryLowAlert via powerPort
            if batteryLevel < 20
            then LowPower;

        transition first On
            accept : BatteryLowAlert via powerPort
            if batteryLevel >= 20
            then On;

        transition first LowPower
            accept : BatteryLowAlert via powerPort
            if batteryLevel < 5
            then Off;

        transition first LowPower
            accept : BatteryLowAlert via powerPort
            if batteryLevel >= 5
            then LowPower;
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
                    isStateActive(system.Behaviour.LowPower)
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
                    and isStateActive(system.Behaviour.LowPower)
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
                    isStateActive(system.Behaviour.LowPower)
                    and isStateActive(system.Behaviour.Off)
                ))
            }
        }
    }
}
