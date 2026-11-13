doc
/*
 * A spacecraft that sends its data to a ground station while it manages its battery.
 * The station pings the spacecraft when it starts operating and every ten seconds after; the spacecraft then transmits while it has data and charge, and its battery recharges in a parallel region.
 * The spacecraft starts a transmission only while it is not recharging; its charge and its data change in steps of ten.
 * The cases ask whether a transmission starts, whether the charge stays above its floor, and whether the spacecraft ever transmits while it recharges.
 */

private import ScalarValues::Integer;
private import ScalarValues::Boolean;
private import SI::s;

part def GroundStation {   
    port commandPort: CommandPort;
    port spacecraftCommandPort: ~CommandPort;
    port dataPort: ~DataPort;
    
    attribute receivedData: Integer := 0;

    exhibit state Behaviour {
        transition first start then Idle;

        state Idle;

        transition first Idle accept after 30 [s] then Operation;
        transition first Idle accept : StartCommand via commandPort then Operation;

        state Operation {
            entry send new PingCommand() via spacecraftCommandPort;

            transition first start then Waiting;

            state Waiting;

            transition
                first Waiting
                accept Data via dataPort
                if receivedData < 100
                do assign receivedData := receivedData + 10
                then Waiting;
        }

        transition first Operation accept after 10 [s] then Operation;
    }
}

part def Spacecraft {
    port commandPort: CommandPort;
    port dataPort: DataPort;

    attribute batteryCharge: Integer := 100;
    attribute recharging: Boolean := false;
    attribute data: Integer := 100;

    exhibit state Behaviour parallel {
        state Communication {
            transition first start then WaitingPing;

            state WaitingPing;

            transition 
                first WaitingPing 
                accept : PingCommand via commandPort 
                if (recharging == false and not (data <= 0 or batteryCharge <= 40))
                then Transmitting;

            state Transmitting parallel {
                state SendData {
                    transition first start then Sending;

                    state Sending;

                    transition 
                        first Sending 
                        accept after 4 [s] 
                        if not (data <= 0 or batteryCharge <= 40) 
                        do action {
                            assign data := data - 10;
                            send new Data() via dataPort;
                        }
                        then Sending;
                }
                state ConsumePower {
                    transition first start then Consuming;

                    state Consuming;

                    transition 
                        first Consuming 
                        accept after 2 [s] 
                        if not (batteryCharge <= 40) 
                        do assign batteryCharge := batteryCharge - 10
                        then Consuming;
                }
            }

            transition 
                first Transmitting
                accept after 4 [s] 
                if data <= 0 or batteryCharge <= 40
                then WaitingPing;
        }

        state Battery {
            transition first start then NotRecharging;

            state NotRecharging {
                entry assign recharging := false;
            }

            state Recharging {
                entry assign recharging := true;
            }
            
            transition 
                first NotRecharging 
                accept after 3 [s]
                if batteryCharge < 80 
                then Recharging;
            
            transition 
                first Recharging 
                accept after 3 [s]
                if batteryCharge < 100
                do assign batteryCharge := batteryCharge + 10 
                then Recharging;

            transition 
                first Recharging 
                accept after 3 [s]
                if batteryCharge >= 100 
                then NotRecharging;
        }
    }
}

item def Data;

item def PingCommand;
item def StartCommand;

port def CommandPort {
    in item ping: PingCommand;
    in item start: StartCommand;
}
port def DataPort {
    out item data: Data;
}

part def Mission {
    port commandPort: CommandPort;

    part groundStation: GroundStation;
    part spacecraft: Spacecraft;

    bind commandPort = groundStation.commandPort; 

    flow from groundStation.spacecraftCommandPort to spacecraft.commandPort;
    flow from spacecraft.dataPort to groundStation.dataPort;
}

verification def SpacecraftBehaviorCommunicationTransmittingReachable {
    subject mission: Mission;

    objective {
        verify requirement {
            require constraint {
                eventually(
                    isStateActive(mission.spacecraft.Behaviour.Communication.Transmitting)
                )
            }
        }
    }
}

verification def SpacecraftBatteryAlwaysAbove40 {
    subject mission: Mission;

    objective {
        verify requirement {
            require constraint {
                mustAlways(
                    mission.spacecraft.batteryCharge >= 40
                )
            }
        }
    }
}

verification def NeverTransmitsWhileRecharging {
    subject mission: Mission;

    objective {
        verify requirement {
            require constraint {
                mustAlways(not(
                    isStateActive(mission.spacecraft.Behaviour.Communication.Transmitting)
                    and isStateActive(mission.spacecraft.Behaviour.Battery.Recharging)
                ))
            }
        }
    }
}
