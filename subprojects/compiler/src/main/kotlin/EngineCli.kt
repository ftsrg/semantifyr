/*
 * SPDX-FileCopyrightText: 2023-2024 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.compiler

import hu.bme.mit.semantifyr.oxsts.compiler.reader.OxstsReader
import hu.bme.mit.semantifyr.oxsts.compiler.reader.prepareOxsts
import hu.bme.mit.semantifyr.oxsts.compiler.serialization.Serializer
import hu.bme.mit.semantifyr.oxsts.compiler.transformation.XstsTransformer
import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import java.io.File

fun main(args: Array<String>) {
    val parser = ArgParser("oxsts")

    val inputDirectory by parser.argument(ArgType.String, "input")
    val libraryDirectory by parser.option(ArgType.String, "library")
    val targetName by parser.argument(ArgType.String, "target")
    val outputFile by parser.argument(ArgType.String, "output")

    parser.parse(args)

    prepareOxsts()

    val reader = OxstsReader(inputDirectory, libraryDirectory ?: "")
    reader.read()

    val transformer = XstsTransformer(reader)

    val xsts = transformer.transform(targetName)
    val xstsString = Serializer.serialize(xsts)

    File(outputFile).writeText(xstsString)
}
