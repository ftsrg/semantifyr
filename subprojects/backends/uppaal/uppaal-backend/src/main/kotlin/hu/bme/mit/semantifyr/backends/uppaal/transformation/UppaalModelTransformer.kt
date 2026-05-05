/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.uppaal.transformation

import com.google.inject.Inject
import hu.bme.mit.semantifyr.backends.uppaal.ir.UppaalIrSerializer
import hu.bme.mit.semantifyr.backends.uppaal.ir.UppaalNta
import hu.bme.mit.semantifyr.oxsts.model.oxsts.EnumDeclaration
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlinedOxsts
import hu.bme.mit.semantifyr.oxsts.model.oxsts.LocalVarDeclarationOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.VariableDeclaration
import org.eclipse.emf.ecore.EObject

data class UppaalArtifacts(
    val modelXml: String,
    val query: String,
)

class UppaalModelTransformer @Inject constructor(
    private val uppaalPropertyTransformer: UppaalPropertyTransformer,
    private val uppaalDeclarationsBuilder: UppaalDeclarationsBuilder,
    private val uppaalTemplateBuilder: UppaalTemplateBuilder,
    private val uppaalIrSerializer: UppaalIrSerializer,
) {

    fun transform(inlinedOxsts: InlinedOxsts): UppaalArtifacts {
        val nta = buildNta(inlinedOxsts)
        val modelXml = uppaalIrSerializer.serialize(nta)
        val query = uppaalPropertyTransformer.transform(inlinedOxsts.property)
        return UppaalArtifacts(modelXml, query)
    }

    internal fun buildNta(inlinedOxsts: InlinedOxsts): UppaalNta {
        val locals = collectLocalVariables(inlinedOxsts)
        val allVariables = inlinedOxsts.variables + locals
        val enums = collectEnumDeclarations(allVariables)
        val declarations = uppaalDeclarationsBuilder.build(allVariables, enums)
        val template = uppaalTemplateBuilder.build(inlinedOxsts)
        return UppaalNta(declarations, listOf(template))
    }

    private fun collectLocalVariables(inlinedOxsts: InlinedOxsts): List<LocalVarDeclarationOperation> {
        val result = mutableListOf<LocalVarDeclarationOperation>()
        collectLocalVariablesFrom(inlinedOxsts.initTransition, result)
        collectLocalVariablesFrom(inlinedOxsts.mainTransition, result)
        return result
    }

    private fun collectLocalVariablesFrom(
        root: EObject,
        sink: MutableList<LocalVarDeclarationOperation>,
    ) {
        val iterator = root.eAllContents()
        while (iterator.hasNext()) {
            val next = iterator.next()
            if (next is LocalVarDeclarationOperation) {
                sink += next
            }
        }
    }

    private fun collectEnumDeclarations(variables: List<VariableDeclaration>): List<EnumDeclaration> {
        return variables.asSequence().mapNotNull {
            it.typeSpecification?.domain as? EnumDeclaration
        }.distinct().toList()
    }

    companion object {
        const val TEMPLATE_NAME = "Model"
        const val STABLE_LOCATION_NAME = "Running"
        const val STARTING_LOCATION_NAME = "Start"
        const val STABLE_LOCATION_ID = "running"
        const val STARTING_LOCATION_ID = "start"
    }
}
