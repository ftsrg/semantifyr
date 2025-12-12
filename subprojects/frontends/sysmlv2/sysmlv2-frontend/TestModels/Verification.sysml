private import ScalarValues::Boolean;

/*
 * Verification constraints with this calculation will be transformed into a 'UNSAFE' expecting verification case.
 */
calc def eventually {
    in op: Boolean[1];
    return : Boolean[1];
}

/*
 * Verification constraints with this calculation will be transformed into a 'SAFE' expecting verification case.
 */
calc def never {
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
