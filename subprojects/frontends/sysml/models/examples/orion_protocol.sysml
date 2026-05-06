private import ScalarValues::Boolean;
private import SI::s;

item def Operation_Call_SendData;
item def Operation_Call_Invalid;
item def OrionDisconn;
item def OrionDisconnCause;
item def OrionConnReq;
item def OrionAppData;
item def OrionKeepAlive;
item def OrionConnConf;
item def OrionConnResp;
item def Operation_Call_Connect;
item def Operation_Call_Disconn;

port def Block_Interface_ForOrion {
    out item operation_Call_SendData : Operation_Call_SendData;
    out item operation_Call_Invalid : Operation_Call_Invalid;
}

port def StateMachine_Interface_For_Orion {
    out item orionDisconn : OrionDisconn;
    out item orionDisconnCause : OrionDisconnCause;
    out item orionConnReq : OrionConnReq;
    out item orionAppData : OrionAppData;
    out item orionKeepAlive : OrionKeepAlive;
    out item orionConnConf : OrionConnConf;
    out item orionConnResp : OrionConnResp;
}

port def Connection_Interface_For_Orion {
    out item operation_Call_Connect : Operation_Call_Connect;
    out item operation_Call_Disconn : Operation_Call_Disconn;
}

part def Orion_Master_SM {
    port Block_Port: ~Block_Interface_ForOrion;
    port Connection_Port: ~Connection_Interface_For_Orion;
    port StateMachine_Port: ~StateMachine_Interface_For_Orion;
    port Send_StateMachine_Port: StateMachine_Interface_For_Orion;
    port Process_StateMachine_Port: StateMachine_Interface_For_Orion;

    attribute is_Connected: Boolean default false;

    exhibit state Behaviour {
        state Closed;
        state Connecting;

        state Connected parallel {
            entry action { assign is_Connected := true; }
            exit action { assign is_Connected := false; }

            state Region1 {
                state KeepAliveSendTimeout;

                transition first start then KeepAliveSendTimeout;

                transition first KeepAliveSendTimeout
                    accept after 4 [s]
                    do action {
                        send OrionKeepAlive() via Send_StateMachine_Port;
                    }
                    then KeepAliveSendTimeout;

                transition first KeepAliveSendTimeout
                    accept : Operation_Call_SendData via Block_Port
                    do action {
                        send OrionAppData() via Send_StateMachine_Port;
                    }
                    then KeepAliveSendTimeout;
            }

            state Region2 {
                state KeepAliveReceiveTimeout;

                transition first start then KeepAliveReceiveTimeout;

                transition first KeepAliveReceiveTimeout
                    accept after 5 [s]
                    then MasterMerge;

                transition first KeepAliveReceiveTimeout
                    accept : OrionAppData via StateMachine_Port
                    do action {
                        send OrionAppData() via Process_StateMachine_Port;
                    }
                    then KeepAliveReceiveTimeout;

                transition first KeepAliveReceiveTimeout
                    accept : OrionKeepAlive via StateMachine_Port
                    then KeepAliveReceiveTimeout;
            }
        }

        state MasterMerge;
        state MasterMerge2;

        transition first start then Closed;

        // From Closed
        transition first Closed
            accept after 5 [s]
            do action {
                send OrionConnReq() via Send_StateMachine_Port;
            }
            then Connecting;

        transition first Closed
            accept : Operation_Call_Connect via Connection_Port
            do action {
                send OrionConnReq() via Send_StateMachine_Port;
            }
            then Connecting;

        // From Connecting
        transition first Connecting
            accept : OrionConnResp via StateMachine_Port
            do action {
                send OrionConnConf() via Send_StateMachine_Port;
            }
            then Connected;

        transition first Connecting
            accept after 5 [s]
            then MasterMerge2;

        transition first Connecting
            accept : OrionDisconnCause via StateMachine_Port
            then Closed;

        transition first Connecting
            accept : Operation_Call_Invalid via Block_Port
            then MasterMerge2;

        transition first Connecting
            accept : OrionConnConf via StateMachine_Port
            then MasterMerge2;

        transition first Connecting
            accept : OrionKeepAlive via StateMachine_Port
            then MasterMerge2;

        transition first Connecting
            accept : OrionAppData via StateMachine_Port
            then MasterMerge2;

        transition first Connecting
            accept : OrionConnReq via StateMachine_Port
            then MasterMerge2;

        // From Connected
        transition first Connected
            accept : Operation_Call_Disconn via Connection_Port
            then MasterMerge;

        transition first Connected
            accept : OrionConnReq via StateMachine_Port
            then MasterMerge;

        transition first Connected
            accept : OrionDisconnCause via StateMachine_Port
            then Closed;

        transition first Connected
            accept : Operation_Call_Invalid via Block_Port
            then MasterMerge;

        transition first Connected
            accept : OrionConnResp via StateMachine_Port
            then MasterMerge;

        transition first Connected
            accept : OrionConnConf via StateMachine_Port
            then MasterMerge;

        // Merge junctions
        transition first MasterMerge
            accept when true
            do action {
                send OrionDisconn() via Send_StateMachine_Port;
            }
            then Closed;

        transition first MasterMerge2
            accept when true
            do action {
                send OrionDisconn() via Send_StateMachine_Port;
            }
            then Closed;
    }
}

part def Orion_Slave_SM {
    port Block_Port: ~Block_Interface_ForOrion;
    port Connection_Port: ~Connection_Interface_For_Orion;
    port StateMachine_Port: ~StateMachine_Interface_For_Orion;
    port Send_StateMachine_Port: StateMachine_Interface_For_Orion;
    port Process_StateMachine_Port: StateMachine_Interface_For_Orion;
    port Handle_StateMachine_Port: StateMachine_Interface_For_Orion;

    exhibit state Behaviour {
        state Closed;
        state Connecting;

        state Connected parallel {
            state Region1 {
                state KeepAliveSendTimeout;

                transition first start then KeepAliveSendTimeout;

                transition first KeepAliveSendTimeout
                    accept after 4 [s]
                    do action {
                        send OrionKeepAlive() via Send_StateMachine_Port;
                    }
                    then KeepAliveSendTimeout;

                transition first KeepAliveSendTimeout
                    accept : Operation_Call_SendData via Block_Port
                    do action {
                        send OrionAppData() via Send_StateMachine_Port;
                    }
                    then KeepAliveSendTimeout;
            }

            state Region2 {
                state KeepAliveReceiveTimeout;

                transition first start then KeepAliveReceiveTimeout;

                transition first KeepAliveReceiveTimeout
                    accept after 5 [s]
                    then SlaveMerge;

                transition first KeepAliveReceiveTimeout
                    accept : OrionAppData via StateMachine_Port
                    do action {
                        send OrionAppData() via Process_StateMachine_Port;
                    }
                    then KeepAliveReceiveTimeout;

                transition first KeepAliveReceiveTimeout
                    accept : OrionKeepAlive via StateMachine_Port
                    then KeepAliveReceiveTimeout;
            }
        }

        state SlaveMerge;
        state SlaveMerge2;

        transition first start then Closed;

        // From Closed
        transition first Closed
            accept : OrionConnReq via StateMachine_Port
            do action {
                send OrionConnResp() via Send_StateMachine_Port;
            }
            then Connecting;

        // From Connecting
        transition first Connecting
            accept : OrionConnConf via StateMachine_Port
            do action {
                send OrionConnConf() via Handle_StateMachine_Port;
            }
            then Connected;

        transition first Connecting
            accept after 5 [s]
            then SlaveMerge2;

        transition first Connecting
            accept : OrionDisconnCause via StateMachine_Port
            then Closed;

        transition first Connecting
            accept : OrionConnResp via StateMachine_Port
            then SlaveMerge2;

        transition first Connecting
            accept : OrionConnReq via StateMachine_Port
            then SlaveMerge2;

        transition first Connecting
            accept : OrionAppData via StateMachine_Port
            then SlaveMerge2;

        transition first Connecting
            accept : Operation_Call_Invalid via Block_Port
            then SlaveMerge2;

        transition first Connecting
            accept : OrionKeepAlive via StateMachine_Port
            then SlaveMerge2;

        // From Connected
        transition first Connected
            accept : Operation_Call_Invalid via Block_Port
            then SlaveMerge;

        transition first Connected
            accept : OrionConnResp via StateMachine_Port
            then SlaveMerge;

        transition first Connected
            accept : OrionConnReq via StateMachine_Port
            then SlaveMerge;

        transition first Connected
            accept : OrionConnConf via StateMachine_Port
            then SlaveMerge;

        transition first Connected
            accept : OrionDisconnCause via StateMachine_Port
            then Closed;

        transition first Connected
            accept : Operation_Call_Disconn via Connection_Port
            then SlaveMerge;

        // Merge junctions
        transition first SlaveMerge
            accept when true
            do action {
                send OrionDisconn() via Send_StateMachine_Port;
            }
            then Closed;

        transition first SlaveMerge2
            accept when true
            do action {
                send OrionDisconn() via Send_StateMachine_Port;
            }
            then Closed;
    }
}

part def OrionSystem {
    port Connection_Port: ~Connection_Interface_For_Orion;

    part master: Orion_Master_SM;
    part slave: Orion_Slave_SM;

    bind Connection_Port = master.Connection_Port;

    flow from master.Send_StateMachine_Port to slave.StateMachine_Port;
    flow from slave.Send_StateMachine_Port to master.StateMachine_Port;
}

verification def MasterClosedReachable {
    subject system : OrionSystem;

    objective {
        verify requirement {
            require constraint {
                eventually(
                    isStateActive(system.master.Behaviour.Closed)
                )
            }
        }
    }
}

verification def MasterConnectingReachable {
    subject system : OrionSystem;

    objective {
        verify requirement {
            require constraint {
                eventually(
                    isStateActive(system.master.Behaviour.Connecting)
                )
            }
        }
    }
}

verification def MasterConnectedReachable {
    subject system : OrionSystem;

    objective {
        verify requirement {
            require constraint {
                eventually(
                    isStateActive(system.master.Behaviour.Connected)
                )
            }
        }
    }
}

verification def SlaveClosedReachable {
    subject system : OrionSystem;

    objective {
        verify requirement {
            require constraint {
                eventually(
                    isStateActive(system.slave.Behaviour.Closed)
                )
            }
        }
    }
}

verification def SlaveConnectedReachable {
    subject system : OrionSystem;

    objective {
        verify requirement {
            require constraint {
                eventually(
                    isStateActive(system.slave.Behaviour.Connected)
                )
            }
        }
    }
}

verification def MasterConnectedAndClosedNeverSimultaneous {
    subject system : OrionSystem;

    objective {
        verify requirement {
            require constraint {
                mustAlways(not(
                    isStateActive(system.master.Behaviour.Connected)
                    and isStateActive(system.master.Behaviour.Closed)
                ))
            }
        }
    }
}

verification def BothMasterAndSlaveConnectedReachable {
    subject system : OrionSystem;

    objective {
        verify requirement {
            require constraint {
                eventually(
                    isStateActive(system.master.Behaviour.Connected)
                    and isStateActive(system.slave.Behaviour.Connected)
                )
            }
        }
    }
}

verification def MasterIsConnectedReachable {
    subject system : OrionSystem;

    objective {
        verify requirement {
            require constraint {
                eventually(
                    system.master.is_Connected
                )
            }
        }
    }
}
