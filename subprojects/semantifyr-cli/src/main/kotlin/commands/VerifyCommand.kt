package hu.bme.mit.semantifyr.cli.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.help
import com.github.ajalt.clikt.parameters.options.multiple
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.file
import com.github.ajalt.clikt.parameters.types.long
import hu.bme.mit.semantifyr.backends.theta.ThetaExecutor
import hu.bme.mit.semantifyr.backends.theta.ThetaRuntimeDetails
import hu.bme.mit.semantifyr.semantics.utils.loggerFactory
import org.eclipse.emf.common.util.URI
import java.io.File
import java.util.concurrent.TimeUnit

class VerifyCommand : CliktCommand("verify") {

    val logger by loggerFactory()

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

    val libraryDirectory by argument().file(mustExist = true, canBeDir = true)
    val targetName by argument()
    val generateWitness by option("-w", "--witness").flag()

    fun runVerification(xstsPath: String): ThetaRuntimeDetails {
        val xstsFile = File(xstsPath)

        logger.info("Executing Theta (v$thetaVersion) on $xstsPath")

        val thetaExecutor = ThetaExecutor(
            thetaVersion,
            thetaConfiguration,
            timeout,
            TimeUnit.MINUTES
        )

        val workingDirectory = xstsFile.parentFile.absolutePath
        val fileName = xstsFile.nameWithoutExtension

        val runtimeDetails = thetaExecutor.run(workingDirectory, fileName)

        logger.info("Verification result: isUnsafe = ${runtimeDetails.isUnsafe}")

        return runtimeDetails
    }

    override fun run() {
//        logger.info("Preparing Xtext Language")
//
//        prepareOxsts()
//
//        logger.info("Reading model $model")
//
//        val reader = OxstsReader(libraryDirectory.path)
//        reader.readModel(model.path)
//
//        logger.info("Compiling target $targetName")
//
//        val transformer = XstsTransformer(reader)
//
//        val xsts = transformer.transform(targetName, rewriteChoice = true)
//        val xstsString = XstsSerializer.serialize(xsts)
//
//        val output = model.path.replace(".oxsts", ".xsts")
//
//        File(output).writeText(xstsString)
//
//        logger.info("Producing xsts file to $output")
//
//        val result = runVerification(output)
//
//        if (generateWitness && result.isUnsafe) {
//            generateWitness(result, reader)
//        }
    }

//    private fun generateWitness(
//        result: ThetaRuntimeDetails,
//        reader: OxstsReader
//    ) {
//        logger.info("Generating witness from ${result.cexPath}")
//
//        prepareCex()
//
//        val cexReader = CexReader()
//        val cex = cexReader.readCexFile(File(result.cexPath))
//
//        val witnessCreator = WitnessCreator(reader)
//
//        val witnessPath = model.path.replace(".oxsts", ".cex.oxsts")
//        val resource = reader.resourceSet.createResource(URI.createFileURI(witnessPath))
//        resource.contents += witnessCreator.createWitness(targetName, cex)
//
//        resource.save(null)
//
//        logger.info("Saved witness to $witnessPath")
//    }

}
