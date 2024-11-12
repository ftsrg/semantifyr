package hu.bme.mit.semantifyr.oxsts.semantifyr.commands

import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.boolean
import com.github.ajalt.clikt.parameters.types.file
import hu.bme.mit.semantifyr.oxsts.semantifyr.reader.OxstsReader
import hu.bme.mit.semantifyr.oxsts.semantifyr.reader.prepareOxsts
import hu.bme.mit.semantifyr.oxsts.semantifyr.serialization.XstsSerializer
import hu.bme.mit.semantifyr.oxsts.semantifyr.theta.CexReader
import hu.bme.mit.semantifyr.oxsts.semantifyr.theta.ThetaRuntimeDetails
import hu.bme.mit.semantifyr.oxsts.semantifyr.theta.WitnessCreator
import hu.bme.mit.semantifyr.oxsts.semantifyr.theta.prepareCex
import hu.bme.mit.semantifyr.oxsts.semantifyr.transformation.XstsTransformer
import hu.bme.mit.semantifyr.oxsts.semantifyr.utils.loggerFactory
import org.eclipse.emf.common.util.URI
import java.io.File

class VerifyCommand : BaseVerifyCommand("verify") {

    override val logger by loggerFactory()

    val libraryDirectory by argument().file(mustExist = true, canBeDir = true)
    val targetName by argument()
    val witness by option("-w", "--witness").boolean().default(false)

    override fun run() {
        logger.info("Preparing Xtext Language")

        prepareOxsts()

        logger.info("Reading model $model")

        val reader = OxstsReader(libraryDirectory.path)
        reader.readModel(model.path)

        logger.info("Compiling target $targetName")

        val transformer = XstsTransformer(reader)

        val xsts = transformer.transform(targetName, rewriteChoice = true)
        val xstsString = XstsSerializer.serialize(xsts)

        val output = model.path.replace(".oxsts", ".xsts")

        File(output).writeText(xstsString)

        logger.info("Producing xsts file to $output")

        val result = runVerification(output)

        if (witness) {
            generateWitness(result, reader)
        }
    }

    private fun generateWitness(
        result: ThetaRuntimeDetails,
        reader: OxstsReader
    ) {
        logger.info("Generating witness from ${result.cexPath}")

        prepareCex()

        val cexReader = CexReader()
        val cex = cexReader.readCexFile(File(result.cexPath))

        val witnessCreator = WitnessCreator(reader)

        val witnessPath = model.path.replace(".oxsts", ".cex.oxsts")
        val resource = reader.resourceSet.createResource(URI.createFileURI(witnessPath))
        resource.contents += witnessCreator.createWitness(targetName, cex)

        resource.save(null)

        logger.info("Saved witness to $witnessPath")
    }

}
