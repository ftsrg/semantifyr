package hu.bme.mit.semantifyr.oxsts.semantifyr.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.file
import hu.bme.mit.semantifyr.oxsts.semantifyr.reader.OxstsReader
import hu.bme.mit.semantifyr.oxsts.semantifyr.reader.prepareOxsts
import hu.bme.mit.semantifyr.oxsts.semantifyr.serialization.Serializer
import hu.bme.mit.semantifyr.oxsts.semantifyr.transformation.XstsTransformer
import org.slf4j.LoggerFactory
import java.io.File

class CompileCommand : CliktCommand("compile") {

    val logger = LoggerFactory.getLogger(CompileCommand::class.java)!!

    val model by argument().file(mustExist = true, canBeFile = true)
    val libraryDirectory by argument().file(mustExist = true, canBeDir = true)
    val targetName by argument()
    val output by option("-o", "--output").file(mustExist = false, canBeFile = true)

    override fun run() {
        logger.info("Preparing Xtext Language")

        prepareOxsts()

        logger.info("Reading model $model")

        val reader = OxstsReader(libraryDirectory.path)
        reader.readModel(model.path)

        logger.info("Compiling target $targetName")

        val transformer = XstsTransformer(reader)

        val xsts = transformer.transform(targetName, rewriteChoice = true)
        val xstsString = Serializer.serialize(xsts)

        val outputFile = output ?: File(model.path.replace(".oxsts", ".xsts"))

        logger.info("Producing xsts file to $outputFile")

        outputFile.writeText(xstsString)
    }

}
