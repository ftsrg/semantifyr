package hu.bme.mit.gamma.oxsts.engine.transformation

import hu.bme.mit.gamma.oxsts.model.oxsts.Operation
import hu.bme.mit.gamma.oxsts.model.oxsts.impl.OxstsFactoryImpl

object OxstsFactory : OxstsFactoryImpl() {
    fun createEmptyOperation(): Operation {
        return createSequenceOperation()
    }
}
