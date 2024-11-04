package hu.bme.mit.semantifyr.oxsts.semantifyr.commands

import hu.bme.mit.semantifyr.oxsts.semantifyr.utils.loggerFactory

class VerifyXstsCommand : BaseVerifyCommand("verify-xsts") {

    override val logger by loggerFactory()

    override fun run() {
        runVerification(model.path)
    }

}
