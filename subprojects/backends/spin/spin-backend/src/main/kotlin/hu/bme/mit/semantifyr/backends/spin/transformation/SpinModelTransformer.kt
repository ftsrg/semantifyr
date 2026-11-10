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
import hu.bme.mit.semantifyr.utils.text.IndentingStringBuilder

// pan reserves 1024 bytes for the state vector by default, and no variable takes more than four bytes of it
private const val DEFAULT_VECTOR_SIZE = 1024
private const val VARIABLE_SIZE_BOUND = 4

data class SpinArtifacts(
    val promela: String,
    val property: SpinProperty,
    val vectorSize: Int,
)

class SpinModelTransformer @Inject constructor(
    private val spinPropertyTransformer: SpinPropertyTransformer,
    private val spinDeclarationRenderer: SpinDeclarationRenderer,
    private val spinProctypeRenderer: SpinProctypeRenderer,
) {

    fun transform(inlinedOxsts: InlinedOxsts): SpinArtifacts {
        val globals = inlinedOxsts.variables
        val enums = collectEnumDeclarations(inlinedOxsts)
        val property = spinPropertyTransformer.transform(inlinedOxsts.property)

        val builder = IndentingStringBuilder()
        spinDeclarationRenderer.renderEnums(builder, enums)
        spinDeclarationRenderer.renderGlobals(builder, globals)
        spinDeclarationRenderer.renderStableFlag(builder)
        spinProctypeRenderer.renderInitProctype(builder, inlinedOxsts)
        builder.appendLine()
        builder.appendLine("ltl p { ${property.ltl} }")

        return SpinArtifacts(builder.toString(), property, vectorSizeOf(inlinedOxsts))
    }

    private fun vectorSizeOf(inlinedOxsts: InlinedOxsts): Int {
        val variables = inlinedOxsts.eAllContents().asSequence().filterIsInstance<VariableDeclaration>().count() + 1
        return DEFAULT_VECTOR_SIZE + VARIABLE_SIZE_BOUND * variables
    }

    private fun collectEnumDeclarations(inlinedOxsts: InlinedOxsts): List<EnumDeclaration> {
        return inlinedOxsts.eAllContents().asSequence().filterIsInstance<VariableDeclaration>().mapNotNull {
            it.typeSpecification?.domain as? EnumDeclaration
        }.distinct().toList()
    }
}
