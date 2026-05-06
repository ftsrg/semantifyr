private import ScalarValues::Boolean;

calc def <EF> eventually {
    in op: Boolean[1];
    return : Boolean[1];
}

calc def <AG> mustAlways {
    in op: Boolean[1];
    return : Boolean[1];
}

/*
 * Evaluates to true iff the specified state is currently active.
 */
calc def isStateActive { 
    in s: SysML::StateUsage[1];
    return : Boolean[1];
}
