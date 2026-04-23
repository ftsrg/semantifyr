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

/**
 * Trim indent and collapse every whitespace run to a single space.
 *
 * Xtext preserves hidden-token whitespace from the input node model within
 * the grammar's min/max ranges when the serializer formats. Collapsing the
 * input to the minimum (one space) forces the formatter to emit min-range
 * output, which matches the output factory-built subtrees produce. Tests
 * parse their fixtures through this so actual and expected serialize
 * identically.
 */
fun String.normalizedFixtureSource(): String {
    return trimIndent().replace(WHITESPACE_RUN, " ")
}

/**
 * Serialize [eObject] with formatting enabled.
 *
 * One small normalization on top: the formatter emits a stray space inside
 * parentheses when the child came from a factory (no node-model tokens).
 * `AG (x)` round-trips fine, but a pass that replaced `x` with a literal
 * produces `AG ( 1)`. The fixup collapses `( +` and ` +)` symmetrically on
 * every call site so the artifact never surfaces in diffs.
 */
fun ISerializer.serializeFormatted(eObject: EObject): String {
    val writer = StringWriter()
    serialize(eObject, writer, SaveOptions.newBuilder().format().options)
    return writer.toString()
        .replace(Regex("\\( +"), "(")
        .replace(Regex(" +\\)"), ")")
}
