/*
 * SPDX-FileCopyrightText: 2023-2024 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.semantifyr

import hu.bme.mit.semantifyr.oxsts.semantifyr.reader.OxstsReader
import hu.bme.mit.semantifyr.oxsts.semantifyr.reader.prepareOxsts
import hu.bme.mit.semantifyr.oxsts.semantifyr.serialization.Serializer
import hu.bme.mit.semantifyr.oxsts.semantifyr.transformation.XstsTransformer
import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import java.io.File

fun main(args: Array<String>) {
    val parser = ArgParser("oxsts")

    val modelPath by parser.argument(ArgType.String, "model")
    val libraryDirectory by parser.argument(ArgType.String, "library")
    val targetName by parser.argument(ArgType.String, "target")
    val output by parser.option(ArgType.String, "output", shortName = "o")

    parser.parse(args)

    prepareOxsts()

    val reader = OxstsReader(libraryDirectory)
    reader.readModel(modelPath)

    val transformer = XstsTransformer(reader)

    val xsts = transformer.transform(targetName)
    val xstsString = Serializer.serialize(xsts)

    val outputPath = output ?: modelPath.replace(".oxsts", ".xsts")

    File(outputPath).writeText(xstsString)
}
