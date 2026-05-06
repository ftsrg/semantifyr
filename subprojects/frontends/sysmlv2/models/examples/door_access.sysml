private import ScalarValues::Integer;

item def BadgeScanned;
item def AccessGranted;
item def DoorOpened;
item def DoorClosed;

port def AccessPort {
    in item badge : BadgeScanned;
    in item grant : AccessGranted;
    in item opened : DoorOpened;
    in item closed : DoorClosed;
}

part def AccessController {
    attribute step: Integer default 0;
    port accessPort: AccessPort;

    exhibit state Behaviour {
        state Locked;
        state Checking;
        state Unlocked;
        state Open;

        transition first start then Locked;

        transition first Locked
            accept : BadgeScanned via accessPort
            do action { assign step := 1; }
            then Checking;

        transition first Checking
            accept : AccessGranted via accessPort
            do action { assign step := 2; }
            then Unlocked;

        transition first Unlocked
            accept : DoorOpened via accessPort
            do action { assign step := 3; }
            then Open;

        transition first Open
            accept : DoorClosed via accessPort
            do action { assign step := 4; }
            then Locked;
    }
}

verification def LockedReachable {
    subject system : AccessController;

    objective {
        verify requirement {
            require constraint {
                eventually(
                    isStateActive(system.Behaviour.Locked)
                )
            }
        }
    }
}

verification def CheckingReachable {
    subject system : AccessController;

    objective {
        verify requirement {
            require constraint {
                eventually(
                    isStateActive(system.Behaviour.Checking)
                )
            }
        }
    }
}

verification def UnlockedReachable {
    subject system : AccessController;

    objective {
        verify requirement {
            require constraint {
                eventually(
                    isStateActive(system.Behaviour.Unlocked)
                )
            }
        }
    }
}

verification def OpenReachable {
    subject system : AccessController;

    objective {
        verify requirement {
            require constraint {
                eventually(
                    isStateActive(system.Behaviour.Open)
                )
            }
        }
    }
}

verification def LockedAndCheckingNeverSimultaneous {
    subject system : AccessController;

    objective {
        verify requirement {
            require constraint {
                mustAlways(not(
                    isStateActive(system.Behaviour.Locked)
                    and isStateActive(system.Behaviour.Checking)
                ))
            }
        }
    }
}

verification def UnlockedAndOpenNeverSimultaneous {
    subject system : AccessController;

    objective {
        verify requirement {
            require constraint {
                mustAlways(not(
                    isStateActive(system.Behaviour.Unlocked)
                    and isStateActive(system.Behaviour.Open)
                ))
            }
        }
    }
}
