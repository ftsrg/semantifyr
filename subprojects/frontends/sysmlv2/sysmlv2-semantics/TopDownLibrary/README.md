# Top-down transition priority

This directory contains an example top-down transition scheduling implementation. Changes compared to the shown Library:

States.oxsts:
```oxsts
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
```
