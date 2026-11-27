item def Oldest;
item def Middle;
item def Newest;

port def Feed {
    out item oldest : Oldest;
    out item middle : Middle;
    out item newest : Newest;
}

part def Producer {
    port output : Feed;

    exhibit state Behaviour {
        state Idle;
        state SentOldest;
        state SentMiddle;
        state SentNewest;
        transition first start then Idle;
        transition first Idle do action { send new Oldest() via output; } then SentOldest;
        transition first SentOldest do action { send new Middle() via output; } then SentMiddle;
        transition first SentMiddle do action { send new Newest() via output; } then SentNewest;
    }
}

part def Consumer {
    port input : ~Feed;

    exhibit state Behaviour {
        state Idle;
        state FirstWait;
        state SecondWait;
        state ThirdWait;
        state Ready;
        state GotOldest;
        state GotMiddle;
        state GotNewest;
        state OutOfOrder;
        transition first start then Idle;
        transition first Idle then FirstWait;
        transition first FirstWait then SecondWait;
        transition first SecondWait then ThirdWait;
        transition first ThirdWait then Ready;
        transition first Ready accept : Oldest via input then GotOldest;
        transition first Ready accept : Middle via input then OutOfOrder;
        transition first GotOldest accept : Middle via input then GotMiddle;
        transition first GotMiddle accept : Newest via input then GotNewest;
    }
}

part def QueueModel {
    part producer : Producer;
    part consumer : Consumer;
    flow from producer.output to consumer.input;
}

verification def QueuedItemIsAcceptedLaterTestCase {
    subject system : QueueModel;
    objective {
        verify requirement {
            require constraint { eventually(isStateActive(system.consumer.Behaviour.GotOldest)) }
        }
    }
}

verification def QueuedItemsAreAcceptedInArrivalOrderTestCase {
    subject system : QueueModel;
    objective {
        verify requirement {
            require constraint { eventually(isStateActive(system.consumer.Behaviour.GotMiddle)) }
        }
    }
}

verification def QueuedItemsAreNeverAcceptedOutOfOrderTestCase {
    subject system : QueueModel;
    objective {
        verify requirement {
            require constraint { mustAlways(not isStateActive(system.consumer.Behaviour.OutOfOrder)) }
        }
    }
}

verification def FullQueueDropsNewestItemTestCase {
    subject system : QueueModel;
    objective {
        verify requirement {
            require constraint { mustAlways(not isStateActive(system.consumer.Behaviour.GotNewest)) }
        }
    }
}
