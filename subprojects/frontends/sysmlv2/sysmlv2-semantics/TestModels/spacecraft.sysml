private import ScalarValues::Integer;
private import ScalarValues::Boolean;
private import SI::s;

part def GroundStation {   
    port commandPort: CommandPort;
    port spacecraftCommandPort: ~CommandPort;
    port dataPort: ~DataPort;
    
    attribute receivedData: Integer default 0;

    exhibit state Behaviour {
        transition first start then Idle;

        state Idle;

        transition first Idle accept after 30 [s] then Operation;
        transition first Idle accept : StartCommand via commandPort then Operation;

        state Operation {
            entry send PingCommand() via spacecraftCommandPort;

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

        // transition first Operation accept : StopCommand via commandPort then Idle;
    }
}

part def Spacecraft {
    port commandPort: CommandPort;
    port dataPort: DataPort;

    attribute batteryCharge: Integer default 100;
    attribute recharging: Boolean default false;
    attribute data: Integer default 100;

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
                            send Data() via dataPort;
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

            transition 
                first Transmitting
                accept after 4 [s] 
                if batteryCharge <= 40
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

item def Data {
    // Internal attributes are not yet supported!
    //attribute value: Integer;
}

item def Command;
item def PingCommand :> Command;
item def StopCommand :> Command;
item def StartCommand :> Command;

port def CommandPort {
    in item command: Command;
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

verification def StationBehaviorIdleReachable {
    subject mission: Mission;

    objective {
        verify requirement {
            require constraint {
                eventually(
                    isStateActive(mission.groundStation.Behaviour.Idle)
                )
            }
        }
    }
}

verification def StationBehaviorOperationReachable {
    subject mission: Mission;

    objective {
        verify requirement {
            require constraint {
                eventually(
                    isStateActive(mission.groundStation.Behaviour.Operation)
                )
            }
        }
    }
}

verification def StationBehaviorOperationWaitingReachable {
    subject mission: Mission;

    objective {
        verify requirement {
            require constraint {
                eventually(
                    isStateActive(mission.groundStation.Behaviour.Operation.Waiting)
                )
            }
        }
    }
}



// verification def SpacecraftBehaviorCommunicationReachable {
//     subject mission: Mission;

//     objective {
//         verify requirement {
//             require constraint {
//                 eventually(
//                     isStateActive(mission.spacecraft.Behaviour.Communication)
//                 )
//             }
//         }
//     }
// }

verification def SpacecraftBehaviorCommunicationWaitingPingReachable {
    subject mission: Mission;

    objective {
        verify requirement {
            require constraint {
                eventually(
                    isStateActive(mission.spacecraft.Behaviour.Communication.WaitingPing)
                )
            }
        }
    }
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

verification def SpacecraftBehaviorCommunicationTransmittingSendDataReachable {
    subject mission: Mission;

    objective {
        verify requirement {
            require constraint {
                eventually(
                    isStateActive(mission.spacecraft.Behaviour.Communication.Transmitting.SendData)
                )
            }
        }
    }
}

verification def SpacecraftBehaviorCommunicationTransmittingSendDataSendingReachable {
    subject mission: Mission;

    objective {
        verify requirement {
            require constraint {
                eventually(
                    isStateActive(mission.spacecraft.Behaviour.Communication.Transmitting.SendData.Sending)
                )
            }
        }
    }
}

verification def SpacecraftBehaviorCommunicationTransmittingConsumePowerReachable {
    subject mission: Mission;

    objective {
        verify requirement {
            require constraint {
                eventually(
                    isStateActive(mission.spacecraft.Behaviour.Communication.Transmitting.ConsumePower)
                )
            }
        }
    }
}

verification def SpacecraftBehaviorCommunicationTransmittingConsumePowerConsumingReachable {
    subject mission: Mission;

    objective {
        verify requirement {
            require constraint {
                eventually(
                    isStateActive(mission.spacecraft.Behaviour.Communication.Transmitting.ConsumePower.Consuming)
                )
            }
        }
    }
}

// verification def SpacecraftBehaviorBatteryReachable {
//     subject mission: Mission;

//     objective {
//         verify requirement {
//             require constraint {
//                 eventually(
//                     isStateActive(mission.spacecraft.Behaviour.Battery)
//                 )
//             }
//         }
//     }
// }

verification def SpacecraftBehaviorBatteryNotRechargingReachable {
    subject mission: Mission;

    objective {
        verify requirement {
            require constraint {
                eventually(
                    isStateActive(mission.spacecraft.Behaviour.Battery.NotRecharging)
                )
            }
        }
    }
}

verification def SpacecraftBehaviorBatteryRechargingReachable {
    subject mission: Mission;

    objective {
        verify requirement {
            require constraint {
                eventually(
                    isStateActive(mission.spacecraft.Behaviour.Battery.Recharging)
                )
            }
        }
    }
}

verification def GroundStationReceivedDataCanReach0 {
    subject mission: Mission;

    objective {
        verify requirement {
            require constraint {
                eventually(
                    mission.groundStation.receivedData >= 0
                )
            }
        }
    }
}

verification def GroundStationReceivedDataCanReach10 {
    subject mission: Mission;

    objective {
        verify requirement {
            require constraint {
                eventually(
                    mission.groundStation.receivedData >= 10
                )
            }
        }
    }
}

verification def GroundStationReceivedDataCanReach20 {
    subject mission: Mission;

    objective {
        verify requirement {
            require constraint {
                eventually(
                    mission.groundStation.receivedData >= 30
                )
            }
        }
    }
}

verification def GroundStationReceivedDataCanReach30 {
    subject mission: Mission;

    objective {
        verify requirement {
            require constraint {
                eventually(
                    mission.groundStation.receivedData >= 30
                )
            }
        }
    }
}

verification def GroundStationReceivedDataCanReach40 {
    subject mission: Mission;

    objective {
        verify requirement {
            require constraint {
                eventually(
                    mission.groundStation.receivedData >= 40
                )
            }
        }
    }
}

verification def GroundStationReceivedDataCanReach50 {
    subject mission: Mission;

    objective {
        verify requirement {
            require constraint {
                eventually(
                    mission.groundStation.receivedData >= 50
                )
            }
        }
    }
}

verification def GroundStationReceivedDataCanReach60 {
    subject mission: Mission;

    objective {
        verify requirement {
            require constraint {
                eventually(
                    mission.groundStation.receivedData >= 60
                )
            }
        }
    }
}

verification def GroundStationReceivedDataCanReach70 {
    subject mission: Mission;

    objective {
        verify requirement {
            require constraint {
                eventually(
                    mission.groundStation.receivedData >= 70
                )
            }
        }
    }
}

/* Takes too long for the 5 minute timeout
verification def GroundStationReceivedDataCanReach80 {
    subject mission: Mission;

    objective {
        verify requirement {
            require constraint {
                eventually(
                    mission.groundStation.receivedData >= 80
                )
            }
        }
    }
}

verification def GroundStationReceivedDataCanReach90 {
    subject mission: Mission;

    objective {
        verify requirement {
            require constraint {
                eventually(
                    mission.groundStation.receivedData >= 90
                )
            }
        }
    }
}

verification def GroundStationReceivedDataCanReach100 {
    subject mission: Mission;

    objective {
        verify requirement {
            require constraint {
                eventually(
                    mission.groundStation.receivedData >= 100
                )
            }
        }
    }
}
*/

verification def SpacecraftDataCanReach100 {
    subject mission: Mission;

    objective {
        verify requirement {
            require constraint {
                eventually(
                    mission.spacecraft.data <= 100
                )
            }
        }
    }
}

verification def SpacecraftDataCanReach90 {
    subject mission: Mission;

    objective {
        verify requirement {
            require constraint {
                eventually(
                    mission.spacecraft.data <= 90
                )
            }
        }
    }
}

verification def SpacecraftDataCanReach80 {
    subject mission: Mission;

    objective {
        verify requirement {
            require constraint {
                eventually(
                    mission.spacecraft.data <= 80
                )
            }
        }
    }
}

verification def SpacecraftDataCanReach70 {
    subject mission: Mission;

    objective {
        verify requirement {
            require constraint {
                eventually(
                    mission.spacecraft.data <= 70
                )
            }
        }
    }
}

verification def SpacecraftDataCanReach60 {
    subject mission: Mission;

    objective {
        verify requirement {
            require constraint {
                eventually(
                    mission.spacecraft.data <= 60
                )
            }
        }
    }
}

verification def SpacecraftDataCanReach50 {
    subject mission: Mission;

    objective {
        verify requirement {
            require constraint {
                eventually(
                    mission.spacecraft.data <= 50
                )
            }
        }
    }
}

verification def SpacecraftDataCanReach40 {
    subject mission: Mission;

    objective {
        verify requirement {
            require constraint {
                eventually(
                    mission.spacecraft.data <= 40
                )
            }
        }
    }
}

verification def SpacecraftDataCanReach30 {
    subject mission: Mission;

    objective {
        verify requirement {
            require constraint {
                eventually(
                    mission.spacecraft.data <= 30
                )
            }
        }
    }
}

/* Takes too long for the 5 minute timeout
verification def SpacecraftDataCanReach20 {
    subject mission: Mission;

    objective {
        verify requirement {
            require constraint {
                eventually(
                    mission.spacecraft.data <= 20
                )
            }
        }
    }
}

verification def SpacecraftDataCanReach10 {
    subject mission: Mission;

    objective {
        verify requirement {
            require constraint {
                eventually(
                    mission.spacecraft.data <= 10
                )
            }
        }
    }
}

verification def SpacecraftDataCanReach0 {
    subject mission: Mission;

    objective {
        verify requirement {
            require constraint {
                eventually(
                    mission.spacecraft.data <= 0
                )
            }
        }
    }
}
*/

verification def SpacecraftBatteryAlwaysAbove0 {
    subject mission: Mission;

    objective {
        verify requirement {
            require constraint {
                mission.spacecraft.batteryCharge >= 0
            }
        }
    }
}

verification def SpacecraftBatteryAlwaysAbove10 {
    subject mission: Mission;

    objective {
        verify requirement {
            require constraint {
                mission.spacecraft.batteryCharge >= 10
            }
        }
    }
}

verification def SpacecraftBatteryAlwaysAbove20 {
    subject mission: Mission;

    objective {
        verify requirement {
            require constraint {
                mission.spacecraft.batteryCharge >= 20
            }
        }
    }
}

verification def SpacecraftBatteryAlwaysAbove30 {
    subject mission: Mission;

    objective {
        verify requirement {
            require constraint {
                mission.spacecraft.batteryCharge >= 30
            }
        }
    }
}

verification def SpacecraftBatteryAlwaysAbove40 {
    subject mission: Mission;

    objective {
        verify requirement {
            require constraint {
                mission.spacecraft.batteryCharge >= 40
            }
        }
    }
}
