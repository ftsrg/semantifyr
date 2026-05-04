/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.uppaal.transformation

import com.google.inject.Inject
import hu.bme.mit.semantifyr.backend.scopes.VerificationScoped
import hu.bme.mit.semantifyr.backends.uppaal.ir.UppaalDeclarations
import hu.bme.mit.semantifyr.backends.uppaal.ir.UppaalEdge
import hu.bme.mit.semantifyr.backends.uppaal.ir.UppaalIrSerializer
import hu.bme.mit.semantifyr.backends.uppaal.ir.UppaalLocation
import hu.bme.mit.semantifyr.backends.uppaal.ir.UppaalLocationKind
import hu.bme.mit.semantifyr.backends.uppaal.ir.UppaalNta
import hu.bme.mit.semantifyr.backends.uppaal.ir.UppaalTemplate
import hu.bme.mit.semantifyr.backends.uppaal.ir.UppaalVariableDecl
import hu.bme.mit.semantifyr.oxsts.model.oxsts.EnumDeclaration
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlinedOxsts
import hu.bme.mit.semantifyr.oxsts.model.oxsts.LocalVarDeclarationOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.VariableDeclaration
import org.eclipse.emf.ecore.EObject

data class UppaalArtifacts(
    val modelXml: String,
    val query: String,
)

@VerificationScoped
class UppaalModelTransformer {
    @Inject
    private lateinit var uppaalVariableTransformer: UppaalVariableTransformer

    @Inject
    private lateinit var uppaalOperationTransformer: UppaalOperationTransformer

    @Inject
    private lateinit var uppaalPropertyTransformer: UppaalPropertyTransformer

    private val serializer = UppaalIrSerializer()

    fun generate(inlinedOxsts: InlinedOxsts): UppaalArtifacts {
        val nta = buildNta(inlinedOxsts)
        val modelXml = serializer.serialize(nta)
        val query = uppaalPropertyTransformer.transform(inlinedOxsts.property)
        return UppaalArtifacts(modelXml = modelXml, query = query)
    }

    internal fun buildNta(inlinedOxsts: InlinedOxsts): UppaalNta {
        val locals = collectLocalVars(inlinedOxsts)
        val allVars: List<VariableDeclaration> = inlinedOxsts.variables + locals
        val variables = allVars.map { uppaalVariableTransformer.describe(it) }
        val enums = collectEnums(allVars)
        val declarations = buildGlobalDeclarations(enums, variables)

        val template = buildTemplate(inlinedOxsts)
        return UppaalNta(
            globalDeclarations = declarations,
            templates = listOf(template),
        )
    }

    private fun collectLocalVars(inlinedOxsts: InlinedOxsts): List<LocalVarDeclarationOperation> {
        val result = mutableListOf<LocalVarDeclarationOperation>()
        collectLocalVarsFrom(inlinedOxsts.initTransition, result)
        collectLocalVarsFrom(inlinedOxsts.mainTransition, result)
        return result
    }

    private fun collectLocalVarsFrom(
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

    private fun collectEnums(variables: List<VariableDeclaration>): List<EnumDeclaration> {
        return variables
            .asSequence()
            .mapNotNull { it.typeSpecification?.domain as? EnumDeclaration }
            .distinct()
            .toList()
    }

    private fun buildGlobalDeclarations(
        enums: List<EnumDeclaration>,
        variables: List<UppaalVariable>,
    ): UppaalDeclarations {
        val typedefs = buildEnumLiteralConstants(enums)
        val clocks = variables.filter { it.kind == UppaalVariableKind.Clock }
        val variableDecls = mutableListOf<UppaalVariableDecl>()
        if (clocks.isNotEmpty()) {
            // Uppaal allows `clock a, b, c;` but our IR is per-variable for
            // clarity; one `clock name;` entry per clock is equivalent.
            for (clock in clocks) {
                variableDecls += UppaalVariableDecl(typeName = "clock", name = clock.name)
            }
        }
        for (variable in variables) {
            when (variable.kind) {
                UppaalVariableKind.Clock -> {
                    // Already handled above.
                }
                UppaalVariableKind.Integer -> {
                    variableDecls += UppaalVariableDecl(
                        typeName = "int",
                        name = variable.name,
                        initialValue = variable.initialValue,
                    )
                }
                UppaalVariableKind.Boolean -> {
                    variableDecls += UppaalVariableDecl(
                        typeName = "bool",
                        name = variable.name,
                        initialValue = variable.initialValue,
                    )
                }
                UppaalVariableKind.Enum -> {
                    variableDecls += UppaalVariableDecl(
                        typeName = "int",
                        name = variable.name,
                        initialValue = variable.initialValue,
                    )
                }
            }
        }
        return UppaalDeclarations(typedefs = typedefs, variables = variableDecls)
    }

    private fun buildEnumLiteralConstants(enums: List<EnumDeclaration>): List<String> {
        val lines = mutableListOf<String>()
        for (enum in enums) {
            lines += "// enum ${enum.name}"
            enum.literals.forEachIndexed { index, literal ->
                val name = uppaalVariableTransformer.sanitizeEnumLiteral(literal)
                lines += "const int $name = $index;"
            }
        }
        return lines
    }

    private fun buildTemplate(inlinedOxsts: InlinedOxsts): UppaalTemplate {
        val startId = STARTING_LOCATION_ID
        val runningId = STABLE_LOCATION_ID
        val context = UppaalOperationTransformer.EmissionContext()

        val initBranches = inlinedOxsts.initTransition.branches
        if (initBranches.isEmpty()) {
            // No init behaviour: the model can immediately reach the
            // stable state.
            context.edges += UppaalEdge(sourceId = startId, targetId = runningId)
        } else {
            for (branch in initBranches) {
                uppaalOperationTransformer.transform(context, startId, runningId, branch)
            }
        }

        for (branch in inlinedOxsts.mainTransition.branches) {
            uppaalOperationTransformer.transform(context, runningId, runningId, branch)
        }

        val locations = mutableListOf(
            UppaalLocation(id = startId, name = STARTING_LOCATION_NAME, kind = UppaalLocationKind.Committed),
            UppaalLocation(id = runningId, name = STABLE_LOCATION_NAME, kind = UppaalLocationKind.Normal),
        )
        locations += context.locations

        return UppaalTemplate(
            name = TEMPLATE_NAME,
            locations = locations,
            initialLocationId = startId,
            edges = context.edges.toList(),
        )
    }

    companion object {
        const val TEMPLATE_NAME: String = "Model"

        const val STABLE_LOCATION_NAME: String = "Running"

        const val STARTING_LOCATION_NAME: String = "Start"

        const val STABLE_LOCATION_ID: String = "running"

        const val STARTING_LOCATION_ID: String = "start"
    }
}
