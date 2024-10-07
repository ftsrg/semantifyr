package hu.bme.mit.semantifyr.oxsts.semantifyr.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.multiple
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.file
import hu.bme.mit.semantifyr.oxsts.semantifyr.theta.ThetaExecutor
import java.io.File

abstract class BaseVerifyCommand(name: String) : CliktCommand(name) {

    val model by argument().file(mustExist = true, canBeFile = true, canBeDir = false)
    val thetaVersion by option().default("6.5.2")
    val thetaConfiguration by option().multiple(
        default = listOf(
            "--domain EXPL --refinement SEQ_ITP --maxenum 250 --initprec CTRL --stacktrace",
            "--domain EXPL_PRED_COMBINED --autoexpl NEWOPERANDS --initprec CTRL --stacktrace",
            "--domain PRED_CART --refinement SEQ_ITP --stacktrace",
            "--stacktrace",
        ),
    )

    fun runVerification(xstsPath: String) {
        val xstsFile = File(xstsPath)


        val thetaExecutor = ThetaExecutor(
            thetaVersion,
            thetaConfiguration,
        )

        val workingDirectory = xstsFile.parentFile.absolutePath
        val fileName = xstsFile.nameWithoutExtension

        val runtimeDetails = thetaExecutor.run(workingDirectory, fileName)

        println(runtimeDetails.isUnsafe)
    }

}
