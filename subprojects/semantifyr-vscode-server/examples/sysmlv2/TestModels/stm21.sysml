item def F;
item def G;

port def InputEvents {
    in item f : F;
    in item g : G;
}

part def Stm21 {
    port input : InputEvents;

    exhibit state Behaviour parallel {
        state R1 {
            state S11;
            state S12;

            transition first start then S11;
            transition
                first S11
                accept : F via input
                then S12;
        }
        state R2 {
            state S13;
            state S14;
            transition first start then S13;
            transition
                first S13
                accept : G via input
                then S14;
        }
    }
}