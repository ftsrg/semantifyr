private import ScalarValues::Integer;
private import ScalarValues::Boolean;
private import SI::s;

part def GroundStation {   
    port commandPort : CommandPort;
    port spacecraftCommandPort : ~CommandPort;
    port dataPort : ~DataPort;
    
    attribute receivedData : Integer default 0;

    exhibit state Behaviour {
        state Idle;

        transition first start then Idle;

        state Operation {
            entry send PingCommand() via spacecraftCommandPort;

            state Waiting;

            transition first start then Waiting;

            transition
                first Waiting
                accept Data via dataPort
                if receivedData < 100
                do assign receivedData := receivedData + 10
                then Waiting;
        }

        transition first Idle accept after 30 [s] then Operation;
        transition first Idle accept : StartCommand via commandPort then Operation;

        transition first Operation accept after 10 [s] then Operation;
        transition first Operation accept : StopCommand via commandPort then Idle;
    }
}

part def Spacecraft {    
    attribute batteryCharge : Integer default 100;
    attribute recharging : Boolean default false;
    attribute data : Integer default 100;

    port commandPort : CommandPort;
    port dataPort : DataPort;

    exhibit state Behaviour parallel {
        state Communication {
            state WaitingPing;

            transition 
                first Transmitting.SendData.Sending 
                accept after 4 [s] 
                if (data <= 0) or (batteryCharge < 40)
                then WaitingPing;

            transition 
                first Transmitting.ConsumePower.Consuming 
                accept after 4 [s] 
                if batteryCharge < 40
                then WaitingPing;

            state Transmitting parallel {
                state SendData {
                    state Sending;

                    transition first start then Sending;

                    transition 
                        first Sending 
                        accept after 4 [s] 
                        if not (data <= 0 or batteryCharge < 40) 
                        do action {
                            assign data := data - 10;
                            send Data() via dataPort;
                        }
                        then Sending;
                }
                state ConsumePower {
                    state Consuming;

                    transition first start then Consuming;

                    transition 
                        first Consuming 
                        accept after 2 [s] 
                        if not (batteryCharge < 40) 
                        do assign batteryCharge := batteryCharge - 10
                        then Consuming;
                }
            }

            transition first start then WaitingPing;
            transition 
                first WaitingPing 
                accept : PingCommand via commandPort 
                if (recharging == false and not (data <= 0 or batteryCharge < 40))
                then Transmitting;
        }

        state Battery {
            state NotRecharging {
                entry assign recharging := false;
            }

            state Recharging {
                entry assign recharging := true;
            }

            transition first start then NotRecharging;
            
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
    //attribute value : Integer;
}

item def Command;
item def PingCommand :> Command;
item def StopCommand :> Command;
item def StartCommand :> Command;

port def CommandPort {
    in item command : Command;
}
port def DataPort {
    out item data : Data;
}

part def Mission {
    port commandPort : CommandPort;

    part groundStation : GroundStation;
    part spacecraft : Spacecraft;

    bind commandPort = groundStation.commandPort; 

    flow from groundStation.commandPort to spacecraft.commandPort;
    flow from spacecraft.dataPort to groundStation.dataPort;
}

verification def StationBehaviorIdleReachable {
    subject mission : Mission;

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
    subject mission : Mission;

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
    subject mission : Mission;

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
//     subject mission : Mission;

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
    subject mission : Mission;

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
    subject mission : Mission;

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
    subject mission : Mission;

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
    subject mission : Mission;

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
    subject mission : Mission;

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
    subject mission : Mission;

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
//     subject mission : Mission;

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
    subject mission : Mission;

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
    subject mission : Mission;

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
