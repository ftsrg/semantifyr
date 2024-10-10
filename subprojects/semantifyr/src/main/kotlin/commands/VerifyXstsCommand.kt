package hu.bme.mit.semantifyr.oxsts.semantifyr.commands

import org.slf4j.LoggerFactory

class VerifyXstsCommand : BaseVerifyCommand("verify-xsts") {

    override val logger = LoggerFactory.getLogger(VerifyXstsCommand::class.java)!!

    override fun run() {
        runVerification(model.path)
    }

}
