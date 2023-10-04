package hu.bme.mit.gamma.oxsts.engine

import hu.bme.mit.gamma.oxsts.engine.reader.OxstsReader
import hu.bme.mit.gamma.oxsts.engine.serialization.FileSerializer
import hu.bme.mit.gamma.oxsts.model.oxsts.Element
import hu.bme.mit.gamma.oxsts.model.oxsts.OxstsPackage
import hu.bme.mit.gamma.oxsts.model.oxsts.Type
import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import org.eclipse.emf.common.util.URI
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl
import org.eclipse.xtext.resource.XtextResource
import java.io.File

fun main(args: Array<String>) {
    val parser = ArgParser("oxsts")
    val inputDirectory by parser.argument(ArgType.String, "input")
    val rootType by parser.argument(ArgType.String, "root")
    val outputFile by parser.argument(ArgType.String, "output")

    parser.parse(args)

    val reader = OxstsReader(inputDirectory)

    reader.read()

    val type = reader.rootElements.flatMap {
        it.eAllContents().asSequence()
    }.map {
        it as? Element
    }.firstOrNull {
        it?.name == rootType
    } as Type?

    requireNotNull(type) { "No type found!" }

    val fileSerializer = FileSerializer(outputFile)
    fileSerializer.serialize(type)
}
