/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.spin.transformation

import com.google.inject.Inject
import hu.bme.mit.semantifyr.oxsts.model.oxsts.EnumDeclaration
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlinedOxsts
import hu.bme.mit.semantifyr.oxsts.model.oxsts.VariableDeclaration
import hu.bme.mit.semantifyr.utils.text.IndentingBuilder

data class SpinArtifacts(
    val promela: String,
    val property: SpinProperty,
)

class SpinModelTransformer @Inject constructor(
    private val spinVariableTransformer: SpinVariableTransformer,
    private val spinPropertyTransformer: SpinPropertyTransformer,
    private val spinDeclarationRenderer: SpinDeclarationRenderer,
    private val spinProctypeRenderer: SpinProctypeRenderer,
) {

    fun transform(inlinedOxsts: InlinedOxsts): SpinArtifacts {
        val globals = inlinedOxsts.variables
        val enums = collectEnumDeclarations(globals)
        val property = spinPropertyTransformer.transform(inlinedOxsts.property)

        val builder = IndentingBuilder()
        spinDeclarationRenderer.renderEnums(builder, enums)
        spinDeclarationRenderer.renderGlobals(builder, globals)
        spinDeclarationRenderer.renderStableFlag(builder)
        spinProctypeRenderer.renderInitProctype(builder, inlinedOxsts)
        builder.line()
        builder.line("ltl p { ${property.ltl} }")

        return SpinArtifacts(builder.toString(), property)
    }

    private fun collectEnumDeclarations(
        globals: List<VariableDeclaration>,
    ): List<EnumDeclaration> {
        return globals.asSequence().map {
            spinVariableTransformer.describe(it)
        }.mapNotNull {
            it.enumDeclaration
        }.distinct().toList()
    }
}
