package hu.bme.mit.gamma.oxsts.engine

import hu.bme.mit.gamma.oxsts.engine.reader.OxstsReader
import hu.bme.mit.gamma.oxsts.engine.serialization.Serializer
import hu.bme.mit.gamma.oxsts.engine.transformation.ImplicitExpressionRewriter
import hu.bme.mit.gamma.oxsts.engine.transformation.XstsTransformer
import hu.bme.mit.gamma.oxsts.model.oxsts.Target
import hu.bme.mit.gamma.oxsts.model.oxsts.Type
import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import java.io.File

fun main(args: Array<String>) {
    val parser = ArgParser("oxsts")
    val inputDirectory by parser.argument(ArgType.String, "input")
    val targetName by parser.argument(ArgType.String, "target")
    val outputFile by parser.argument(ArgType.String, "output")

    parser.parse(args)

    val reader = OxstsReader(inputDirectory)

    reader.read()

    val target = reader.rootElements.flatMap {
        it.eAllContents().asSequence()
    }.filterIsInstance<Target>().firstOrNull {
        it.name == targetName
    }

    requireNotNull(target) { "Target not found!" }

    val types = reader.rootElements.flatMap { it.eAllContents().asSequence() }.filterIsInstance<Type>()
    for (type in types) {
        ImplicitExpressionRewriter.rewriteExpressions(type)
    }
    ImplicitExpressionRewriter.rewriteExpressions(target)

    val transformer = XstsTransformer()
    val xsts = transformer.transform(target)

    val xstsString = Serializer.serialize(xsts)

    File(outputFile).writeText(xstsString)

}
