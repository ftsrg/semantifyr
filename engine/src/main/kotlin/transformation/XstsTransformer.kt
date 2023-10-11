package hu.bme.mit.gamma.oxsts.engine.transformation

import hu.bme.mit.gamma.oxsts.model.oxsts.Target
import hu.bme.mit.gamma.oxsts.model.oxsts.XSTS

class XstsTransformer {
    fun transform(target: Target): XSTS {
        val instantiator = Instantiator()
        val rootInstance = instantiator.instantiate(target)

        val xsts = OxstsFactory.createXSTS()

        target.variables.map { it.typing }
        xsts.variables.addAll(target.variables.map{ it.copy() })
        xsts.init = target.init.copy()
        xsts.transition = target.transition.copy()
        xsts.property = target.property.copy()

        return xsts
    }

}
