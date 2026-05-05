/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler.pipeline.utils

import org.eclipse.emf.ecore.EObject
import org.eclipse.xtext.resource.SaveOptions
import org.eclipse.xtext.serializer.ISerializer
import java.io.StringWriter

private val WHITESPACE_RUN = Regex("\\s+")

fun String.normalizedFixtureSource(): String {
    return trimIndent().replace(WHITESPACE_RUN, " ")
}

fun ISerializer.serializeFormatted(eObject: EObject): String {
    val writer = StringWriter()
    serialize(eObject, writer, SaveOptions.newBuilder().format().options)
    return writer.toString()
        .replace(Regex("\\( +"), "(")
        .replace(Regex(" +\\)"), ")")
}
