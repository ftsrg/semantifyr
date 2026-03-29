# Top-down transition priority

Changes compared to the Library:

States.oxsts:

redefine tran fireTransitions() {
    choice {
        // commented out
        // inline assumeNoInnerTransitionEnabled()
        inline fireLocalTransitions()
    } or {
        // added
        // specifies top-down transition priority
        inline assumeNoLocalTransitionEnabled()
        inline fireInnerTransitions()
    }
}
