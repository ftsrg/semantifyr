item def EngageAutonomous;
item def DisengageAutonomous;
item def PowerOff;
item def DetectHighway;
item def DetectUrban;
item def FrontObstacle;
item def FrontClear;
item def RearObstacle;
item def RearClear;
item def PedestrianDetected;
item def PedestrianClear;
item def AllClear;
item def NearExit;
item def IntersectionDetected;
item def Proceed;

port def EnvironmentPort {
    in item engageAutonomous : EngageAutonomous;
    in item disengageAutonomous : DisengageAutonomous;
    in item powerOff : PowerOff;
    in item detectHighway : DetectHighway;
    in item detectUrban : DetectUrban;
    in item frontObstacle : FrontObstacle;
    in item frontClear : FrontClear;
    in item rearObstacle : RearObstacle;
    in item rearClear : RearClear;
    in item pedestrianDetected : PedestrianDetected;
    in item pedestrianClear : PedestrianClear;
    in item allClear : AllClear;
    in item nearExit : NearExit;
    in item intersectionDetected : IntersectionDetected;
    in item proceed : Proceed;
}

part def AutonomousVehicle {
    port environment: EnvironmentPort;

    exhibit state Behaviour {
        state HumanMode;

        state AutonomousMode parallel {
            state AutonomousDriving {
                state Initialize;

                state HighwayDriving {
                    state EnterHighway;
                    state Cruise;
                    state LaneChange;
                    state ExitHighway;

                    transition first start then EnterHighway;

                    transition first EnterHighway
                        accept : Proceed via environment
                        then Cruise;

                    transition first Cruise
                        accept : FrontObstacle via environment
                        then LaneChange;

                    transition first EnterHighway
                        accept : FrontObstacle via environment
                        then LaneChange;

                    transition first LaneChange
                        accept : Proceed via environment
                        then Cruise;

                    transition first LaneChange
                        accept : NearExit via environment
                        then ExitHighway;

                    transition first Cruise
                        accept : NearExit via environment
                        then ExitHighway;
                }

                state UrbanDriving {
                    state EnterUrban;
                    state Straight;
                    state Intersection;
                    state UrbanLaneChange;
                    state ExitUrban;

                    transition first start then EnterUrban;

                    transition first EnterUrban
                        accept : Proceed via environment
                        then Straight;

                    transition first EnterUrban
                        accept : IntersectionDetected via environment
                        then Intersection;

                    transition first Straight
                        accept : IntersectionDetected via environment
                        then Intersection;

                    transition first Intersection
                        accept : Proceed via environment
                        then Straight;

                    transition first Straight
                        accept : FrontObstacle via environment
                        then UrbanLaneChange;

                    transition first EnterUrban
                        accept : FrontObstacle via environment
                        then UrbanLaneChange;

                    transition first UrbanLaneChange
                        accept : Proceed via environment
                        then Straight;

                    transition first UrbanLaneChange
                        accept : NearExit via environment
                        then ExitUrban;

                    transition first Straight
                        accept : NearExit via environment
                        then ExitUrban;
                }

                state Finish;

                transition first start then Initialize;

                transition first Initialize
                    accept : DetectHighway via environment
                    then HighwayDriving;

                transition first Initialize
                    accept : DetectUrban via environment
                    then UrbanDriving;

                transition first HighwayDriving
                    accept : PowerOff via environment
                    then Finish;

                transition first UrbanDriving
                    accept : PowerOff via environment
                    then Finish;

                transition first HighwayDriving
                    accept : DetectHighway via environment
                    then HighwayDriving;

                transition first UrbanDriving
                    accept : DetectUrban via environment
                    then UrbanDriving;
            }

            state ObstacleDetection {
                state CollisionAvoidanceDeactive;

                state CollisionAvoidanceActive {
                    state CollisionAvoidance parallel {
                        state Frontend {
                            state FrontInactive;
                            state AvoidFrontendCollision;

                            transition first start then FrontInactive;

                            transition first FrontInactive
                                accept : FrontObstacle via environment
                                then AvoidFrontendCollision;

                            transition first AvoidFrontendCollision
                                accept : FrontClear via environment
                                then FrontInactive;
                        }

                        state Rearend {
                            state RearInactive;
                            state AvoidRearendCollision;

                            transition first start then RearInactive;

                            transition first RearInactive
                                accept : RearObstacle via environment
                                then AvoidRearendCollision;

                            transition first AvoidRearendCollision
                                accept : RearClear via environment
                                then RearInactive;
                        }

                        state Pedestrian {
                            state PedestrianInactive;
                            state AvoidPedestrianCollision;

                            transition first start then PedestrianInactive;

                            transition first PedestrianInactive
                                accept : PedestrianDetected via environment
                                then AvoidPedestrianCollision;

                            transition first AvoidPedestrianCollision
                                accept : PedestrianClear via environment
                                then PedestrianInactive;
                        }
                    }

                    state CollisionAvoidanceDone;

                    transition first start then CollisionAvoidance;

                    transition first CollisionAvoidance
                        accept : AllClear via environment
                        then CollisionAvoidanceDone;
                }

                transition first start then CollisionAvoidanceDeactive;

                transition first CollisionAvoidanceDeactive
                    accept : FrontObstacle via environment
                    then CollisionAvoidanceActive;

                transition first CollisionAvoidanceDeactive
                    accept : RearObstacle via environment
                    then CollisionAvoidanceActive;

                transition first CollisionAvoidanceDeactive
                    accept : PedestrianDetected via environment
                    then CollisionAvoidanceActive;

                transition first CollisionAvoidanceActive
                    accept : AllClear via environment
                    then CollisionAvoidanceDeactive;
            }
        }

        state Done;

        transition first start then HumanMode;

        transition first HumanMode
            accept : EngageAutonomous via environment
            then AutonomousMode;

        transition first AutonomousMode
            accept : DisengageAutonomous via environment
            then HumanMode;

        transition first HumanMode
            accept : PowerOff via environment
            then Done;

        transition first AutonomousMode
            accept : PowerOff via environment
            then Done;
    }
}

verification def HumanModeReachable {
    subject system : AutonomousVehicle;

    objective {
        verify requirement {
            require constraint {
                eventually(
                    isStateActive(system.Behaviour.HumanMode)
                )
            }
        }
    }
}

verification def AutonomousModeReachable {
    subject system : AutonomousVehicle;

    objective {
        verify requirement {
            require constraint {
                eventually(
                    isStateActive(system.Behaviour.AutonomousMode)
                )
            }
        }
    }
}

verification def CruiseReachable {
    subject system : AutonomousVehicle;

    objective {
        verify requirement {
            require constraint {
                eventually(
                    isStateActive(system.Behaviour.AutonomousMode.AutonomousDriving.HighwayDriving.Cruise)
                )
            }
        }
    }
}

verification def IntersectionReachable {
    subject system : AutonomousVehicle;

    objective {
        verify requirement {
            require constraint {
                eventually(
                    isStateActive(system.Behaviour.AutonomousMode.AutonomousDriving.UrbanDriving.Intersection)
                )
            }
        }
    }
}

verification def CollisionAvoidanceActiveReachable {
    subject system : AutonomousVehicle;

    objective {
        verify requirement {
            require constraint {
                eventually(
                    isStateActive(system.Behaviour.AutonomousMode.ObstacleDetection.CollisionAvoidanceActive)
                )
            }
        }
    }
}

verification def AvoidFrontendCollisionReachable {
    subject system : AutonomousVehicle;

    objective {
        verify requirement {
            require constraint {
                eventually(
                    isStateActive(system.Behaviour.AutonomousMode.ObstacleDetection.CollisionAvoidanceActive.CollisionAvoidance.Frontend.AvoidFrontendCollision)
                )
            }
        }
    }
}

verification def DoneReachable {
    subject system : AutonomousVehicle;

    objective {
        verify requirement {
            require constraint {
                eventually(
                    isStateActive(system.Behaviour.Done)
                )
            }
        }
    }
}

verification def HumanModeAndDoneNeverSimultaneous {
    subject system : AutonomousVehicle;

    objective {
        verify requirement {
            require constraint {
                mustAlways(not(
                    isStateActive(system.Behaviour.HumanMode)
                    and isStateActive(system.Behaviour.Done)
                ))
            }
        }
    }
}
