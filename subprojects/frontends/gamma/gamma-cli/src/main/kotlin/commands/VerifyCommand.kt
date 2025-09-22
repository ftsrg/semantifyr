///*
// * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
// *
// * SPDX-License-Identifier: EPL-2.0
// */
//
//package hu.bme.mit.semantifyr.frontends.gamma.cli.commands
//
//import com.github.ajalt.clikt.core.CliktCommand
//import com.github.ajalt.clikt.parameters.arguments.argument
//import com.github.ajalt.clikt.parameters.options.default
//import com.github.ajalt.clikt.parameters.options.flag
//import com.github.ajalt.clikt.parameters.options.help
//import com.github.ajalt.clikt.parameters.options.multiple
//import com.github.ajalt.clikt.parameters.options.option
//import com.github.ajalt.clikt.parameters.types.file
//import com.github.ajalt.clikt.parameters.types.long
//import hu.bme.mit.semantifyr.frontends.gamma.frontend.reader.GammaReader
//import hu.bme.mit.semantifyr.frontends.gamma.frontend.reader.prepareGamma
//import hu.bme.mit.semantifyr.frontends.gamma.frontend.serialization.GammaToOxstsSerializer
//import hu.bme.mit.semantifyr.oxsts.semantifyr.reader.OxstsReader
//import hu.bme.mit.semantifyr.oxsts.semantifyr.reader.prepareOxsts
//import hu.bme.mit.semantifyr.oxsts.semantifyr.serialization.XstsSerializer
//import hu.bme.mit.semantifyr.oxsts.semantifyr.theta.CexReader
//import hu.bme.mit.semantifyr.oxsts.semantifyr.theta.ThetaExecutor
//import hu.bme.mit.semantifyr.oxsts.semantifyr.theta.ThetaRuntimeDetails
//import hu.bme.mit.semantifyr.oxsts.semantifyr.theta.WitnessCreator
//import hu.bme.mit.semantifyr.oxsts.semantifyr.theta.prepareCex
//import hu.bme.mit.semantifyr.oxsts.semantifyr.transformation.XstsTransformer
//import hu.bme.mit.semantifyr.oxsts.semantifyr.utils.loggerFactory
//import org.eclipse.emf.common.util.URI
//import java.io.File
//import java.util.concurrent.TimeUnit
//
//class VerifyCommand : CliktCommand("verify") {
//
//    val logger by loggerFactory()
//
//    val modelPath by argument("-m", "--model-path").file(mustExist = true, canBeFile = true)
//    val verificationCase by argument("-v", "--verification-case")
//    val libraryPath by argument("-l", "--library-path").file(mustExist = true, canBeFile = true)
//    val oxstsPath by option("-oxsts", "--oxsts-path").file(mustExist = false, canBeFile = true)
//    val timeout by option().long().default(5).help("Timeout in minutes")
//    val thetaVersion by option().default("6.5.2")
//    val thetaConfiguration by option().multiple(
//        default = listOf(
//            "--domain EXPL --refinement SEQ_ITP --maxenum 250 --initprec CTRL --stacktrace",
//            "--domain EXPL_PRED_COMBINED --autoexpl NEWOPERANDS --initprec CTRL --stacktrace",
//            "--domain PRED_CART --refinement SEQ_ITP --stacktrace",
//            "--stacktrace",
//        ),
//    )
//    val generateWitness by option("-w", "--witness").flag()
//
//    override fun run() {
//        val oxstsFile = transformGammaToOxsts()
//        val oxstsReader = readOxstsModel(oxstsFile)
//
//        val xstsFilePath = oxstsFile.path.replace(".oxsts", ".xsts")
//        val xstsFile = File(xstsFilePath)
//
//        compileOxstsToXsts(oxstsReader, xstsFile)
//        val result = runVerification(xstsFile)
//
//        if (generateWitness && result.isUnsafe) {
//            createVerificationWitness(result, oxstsReader, oxstsFile)
//        }
//    }
//
//    private fun createVerificationWitness(
//        result: ThetaRuntimeDetails,
//        oxstsReader: OxstsReader,
//        oxstsFile: File
//    ) {
//        logger.info("Generating witness from ${result.cexPath}")
//
//        prepareCex()
//
//        val cexReader = CexReader()
//        val cex = cexReader.readCexFile(File(result.cexPath))
//
//        val witnessCreator = WitnessCreator(oxstsReader)
//
//        val witnessPath = oxstsFile.path.replace(".oxsts", ".cex.oxsts")
//        val resource = oxstsReader.resourceSet.createResource(URI.createFileURI(witnessPath))
//        resource.contents += witnessCreator.createWitness(verificationCase, cex)
//
//        resource.save(null)
//
//        logger.info("Saved witness to $witnessPath")
//    }
//
//    private fun readOxstsModel(oxstsFile: File): OxstsReader {
//        prepareOxsts()
//
//        logger.info("Reading OXSTS model: ${oxstsFile.path}")
//
//        val oxstsReader = OxstsReader(libraryPath.path)
//        oxstsReader.readModel(oxstsFile.path)
//
//        return oxstsReader
//    }
//
//    private fun compileOxstsToXsts(oxstsReader: OxstsReader, outputFile: File) {
//        logger.info("Compiling target $verificationCase")
//
//        val transformer = XstsTransformer(oxstsReader)
//
//        val xsts = transformer.transform(verificationCase, rewriteChoice = true)
//        val xstsString = XstsSerializer.serialize(xsts)
//
//        logger.info("Producing xsts file to ${outputFile.path}")
//
//        outputFile.writeText(xstsString)
//    }
//
//    private fun transformGammaToOxsts(): File {
//        logger.info("Preparing Gamma Language")
//        prepareGamma()
//
//        logger.info("Reading Gamma model: $modelPath")
//        val reader = GammaReader()
//        val model = reader.readGammaFile(modelPath)
//
//        logger.info("Serializing OXSTS model")
//        val oxstsModel = GammaToOxstsSerializer.serialize(model)
//        val oxstsFile = oxstsPath ?: File(modelPath.absolutePath.replace(".gamma", ".oxsts"))
//
//        oxstsFile.writeText(oxstsModel)
//        return oxstsFile
//    }
//
//    fun runVerification(xstsFile: File): ThetaRuntimeDetails {
//        logger.info("Executing Theta (v$thetaVersion) on ${xstsFile.path}")
//
//        val thetaExecutor = ThetaExecutor(
//            thetaVersion,
//            thetaConfiguration,
//            timeout,
//            TimeUnit.MINUTES
//        )
//
//        val workingDirectory = xstsFile.parentFile.absolutePath
//        val fileName = xstsFile.nameWithoutExtension
//
//        val runtimeDetails = thetaExecutor.run(workingDirectory, fileName)
//
//        logger.info("Verification result: isUnsafe = ${runtimeDetails.isUnsafe}")
//
//        return runtimeDetails
//    }
//
//}
