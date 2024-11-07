package hu.bme.mit.semantifyr.oxsts.semantifyr.commands

import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.types.file
import hu.bme.mit.semantifyr.oxsts.semantifyr.reader.OxstsReader
import hu.bme.mit.semantifyr.oxsts.semantifyr.reader.prepareOxsts
import hu.bme.mit.semantifyr.oxsts.semantifyr.serialization.XstsSerializer
import hu.bme.mit.semantifyr.oxsts.semantifyr.transformation.XstsTransformer
import hu.bme.mit.semantifyr.oxsts.semantifyr.utils.loggerFactory
import java.io.File

class VerifyCommand : BaseVerifyCommand("verify") {

    override val logger by loggerFactory()

    val libraryDirectory by argument().file(mustExist = true, canBeDir = true)
    val targetName by argument()

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

        runVerification(output)
    }

}
