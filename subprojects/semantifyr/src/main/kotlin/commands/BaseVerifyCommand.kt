package hu.bme.mit.semantifyr.oxsts.semantifyr.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.help
import com.github.ajalt.clikt.parameters.options.multiple
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.file
import com.github.ajalt.clikt.parameters.types.long
import hu.bme.mit.semantifyr.oxsts.semantifyr.theta.ThetaDockerExecutor
import hu.bme.mit.semantifyr.oxsts.semantifyr.theta.ThetaExecutor
import hu.bme.mit.semantifyr.oxsts.semantifyr.theta.ThetaRuntimeDetails
import hu.bme.mit.semantifyr.oxsts.semantifyr.theta.ThetaShellExecutor
import org.slf4j.Logger
import java.io.File
import java.util.concurrent.TimeUnit

abstract class BaseVerifyCommand(name: String) : CliktCommand(name) {

    abstract val logger: Logger

    val model by argument().file(mustExist = true, canBeFile = true, canBeDir = false)
    val timeout by option().long().default(5).help("Timeout in minutes")
    val thetaVersion by option().default("6.5.2")
    val thetaConfiguration by option().multiple(
        default = listOf(
            "--domain EXPL --refinement SEQ_ITP --maxenum 250 --initprec CTRL --stacktrace",
            "--domain EXPL_PRED_COMBINED --autoexpl NEWOPERANDS --initprec CTRL --stacktrace",
            "--domain PRED_CART --refinement SEQ_ITP --stacktrace",
            "--stacktrace",
        ),
    )

    fun runVerification(xstsPath: String, thetaStartPath: String = ""): ThetaRuntimeDetails {
        val xstsFile = File(xstsPath)

        val workingDirectory = xstsFile.parentFile.absolutePath
        val fileName = xstsFile.nameWithoutExtension
        val thetaExecutor : ThetaExecutor = if(thetaStartPath!="") {
            // using a local release (Theta-xsts.zip)
            logger.info("Executing local Theta from JAR ($thetaStartPath) on $xstsPath")
            ThetaShellExecutor(
                thetaStartPath,
                thetaConfiguration,
                timeout,
                TimeUnit.MINUTES
            )
        } else {
            // using docker
            logger.info("Executing Theta (v$thetaVersion) on $xstsPath")
            ThetaDockerExecutor(
                thetaVersion,
                thetaConfiguration,
                timeout,
                TimeUnit.MINUTES
            )
        }

        val runtimeDetails = thetaExecutor.run(workingDirectory, fileName)
        logger.info("Verification result: isUnsafe = ${runtimeDetails.isUnsafe}")

        return runtimeDetails
    }

}
