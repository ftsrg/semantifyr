/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.nuxmv.transformation

import com.google.inject.Inject
import hu.bme.mit.semantifyr.backend.scopes.VerificationScoped
import hu.bme.mit.semantifyr.backend.text.IndentingBuilder
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlinedOxsts
import hu.bme.mit.semantifyr.oxsts.model.oxsts.LocalVarDeclarationOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.VariableDeclaration
import org.eclipse.emf.ecore.EObject

data class NuxmvArtifacts(
    val smv: String,
    val property: NuxmvProperty,
)

@VerificationScoped
class NuxmvModelGenerator {
    @Inject
    private lateinit var nuxmvVariableTransformer: NuxmvVariableTransformer

    @Inject
    private lateinit var nuxmvExpressionTransformer: NuxmvExpressionTransformer

    @Inject
    private lateinit var nuxmvOperationTransformer: NuxmvOperationTransformer

    @Inject
    private lateinit var nuxmvPropertyTransformer: NuxmvPropertyTransformer

    fun generate(inlinedOxsts: InlinedOxsts): NuxmvArtifacts {
        val locals = collectLocalVars(inlinedOxsts)
        val allVars: List<VariableDeclaration> = inlinedOxsts.variables + locals
        val variables = allVars.map { nuxmvVariableTransformer.describe(it) }

        val declaredInitial = allVars
            .mapNotNull { decl ->
                decl.expression?.let { init -> decl to nuxmvExpressionTransformer.transform(init) }
            }.toMap()

        // Init branches: pre-seed each top-level variable with a phase-0 prime, decoupling
        // intermediate values from the SMV variable's INIT value. Declared initial values become
        // the first primed binding; uninitialized variables get a free primed declaration.
        val initBranches = inlinedOxsts.initTransition.branches.mapIndexed { branchIdx, branch ->
            val seed = seedInitBranch(allVars, declaredInitial)
            nuxmvOperationTransformer.transform(branch, contextTag = "init", branchTag = "b$branchIdx", seed = seed)
        }

        // Trans branches: no pre-seed - reads of unprimed variables resolve to the SMV variable
        // name (the current pre-transition state), which is correct for trans guards. Assignments
        // and havocs introduce primes that bind to next(var) at finalize time.
        val tranBranches = inlinedOxsts.mainTransition.branches.mapIndexed { branchIdx, branch ->
            nuxmvOperationTransformer.transform(branch, contextTag = "tran", branchTag = "b$branchIdx")
        }

        val initPrimedVars = initBranches.flatMap { it.newPrimes }
        val tranPrimedVars = tranBranches.flatMap { it.newPrimes }
        val allIvars = (initBranches + tranBranches).flatMap { it.ivars }
        val allFrozenVars = (initBranches + tranBranches).flatMap { it.frozenVars }

        val builder = IndentingBuilder()
        builder.line("MODULE main")
        renderVarSection(builder, variables, initPrimedVars + tranPrimedVars)
        if (allIvars.isNotEmpty()) {
            renderIvarSection(builder, allIvars)
        }
        if (allFrozenVars.isNotEmpty()) {
            renderFrozenVarSection(builder, allFrozenVars)
        }
        renderInitSection(builder, allVars, initBranches, declaredInitial)
        renderTransSection(builder, allVars, tranBranches)

        return NuxmvArtifacts(
            smv = builder.toString(),
            property = nuxmvPropertyTransformer.transform(inlinedOxsts.property),
        )
    }

    private fun renderVarSection(
        builder: IndentingBuilder,
        variables: List<NuxmvVariable>,
        primedVars: List<NuxmvPrimedDecl>,
    ) {
        builder.line("VAR")
        builder.indented {
            for (variable in variables) {
                line("${variable.name} : ${renderType(variable)};")
            }
            for (primed in primedVars) {
                val v = nuxmvVariableTransformer.describe(primed.variable)
                line("${primed.name} : ${renderType(v)};")
            }
        }
    }

    private fun renderIvarSection(
        builder: IndentingBuilder,
        ivars: List<NuxmvIVar>,
    ) {
        builder.line("IVAR")
        builder.indented {
            for (ivar in ivars) {
                line("${ivar.name} : ${ivar.typeSmv};")
            }
        }
    }

    private fun renderFrozenVarSection(
        builder: IndentingBuilder,
        frozenVars: List<NuxmvFrozenVar>,
    ) {
        builder.line("FROZENVAR")
        builder.indented {
            for (frozen in frozenVars) {
                line("${frozen.name} : ${frozen.typeSmv};")
            }
        }
    }

    private fun renderInitSection(
        builder: IndentingBuilder,
        allVars: List<VariableDeclaration>,
        branches: List<NuxmvBranch>,
        declaredInitial: Map<VariableDeclaration, String>,
    ) {
        builder.line("INIT")
        builder.indented {
            val rendered = if (branches.isEmpty()) {
                listOf(
                    finalizeInitBindings(allVars, NuxmvBranch(), declaredInitial),
                )
            } else {
                branches.map { branch ->
                    val finalize = finalizeInitBindings(allVars, branch, declaredInitial)
                    val parts = branch.constraints + finalize
                    parts.filter { it.isNotEmpty() }.joinToString(" & ") { "($it)" }
                        .ifEmpty { "TRUE" }
                }
            }
            line(rendered.joinToString(" | ") { "($it)" })
        }
    }

    private fun renderTransSection(
        builder: IndentingBuilder,
        allVars: List<VariableDeclaration>,
        branches: List<NuxmvBranch>,
    ) {
        builder.line("TRANS")
        builder.indented {
            val rendered = if (branches.isEmpty()) {
                listOf(finalizeTransBindings(allVars, NuxmvBranch()))
            } else {
                branches.map { branch ->
                    val finalize = finalizeTransBindings(allVars, branch)
                    val parts = branch.constraints + finalize
                    parts.filter { it.isNotEmpty() }.joinToString(" & ") { "($it)" }
                        .ifEmpty { "TRUE" }
                }
            }
            line(rendered.joinToString(" | ") { "($it)" })
        }
    }

    /**
     * Build the seed branch for an init transition: every top-level variable gets a phase-0
     * primed name; declared-initial variables also get the binding constraint `<prime> = <init>`.
     */
    private fun seedInitBranch(
        allVars: List<VariableDeclaration>,
        declaredInitial: Map<VariableDeclaration, String>,
    ): NuxmvBranch {
        val constraints = mutableListOf<String>()
        val currentPrime = mutableMapOf<VariableDeclaration, String>()
        val newPrimes = mutableListOf<NuxmvPrimedDecl>()
        for (variable in allVars) {
            val base = nuxmvVariableTransformer.nameOf(variable)
            val name = "${base}__init_seed_0"
            val primed = NuxmvPrimedDecl(name = name, variable = variable)
            newPrimes += primed
            currentPrime[variable] = name
            declaredInitial[variable]?.let { initExpr -> constraints += "$name = $initExpr" }
        }
        return NuxmvBranch(
            constraints = constraints,
            currentPrime = currentPrime,
            newPrimes = newPrimes,
        )
    }

    private fun finalizeInitBindings(
        allVars: List<VariableDeclaration>,
        branch: NuxmvBranch,
        declaredInitial: Map<VariableDeclaration, String>,
    ): String {
        val parts = mutableListOf<String>()
        for (variable in allVars) {
            val name = nuxmvVariableTransformer.nameOf(variable)
            val rhs = branch.currentPrime[variable]
            if (rhs != null && rhs != name) {
                parts += "$name = $rhs"
            } else {
                declaredInitial[variable]?.let { parts += "$name = $it" }
            }
        }
        return parts.joinToString(" & ")
    }

    private fun finalizeTransBindings(
        allVars: List<VariableDeclaration>,
        branch: NuxmvBranch,
    ): String {
        val parts = mutableListOf<String>()
        for (variable in allVars) {
            val name = nuxmvVariableTransformer.nameOf(variable)
            val rhs = branch.currentPrime[variable] ?: name
            parts += "next($name) = $rhs"
        }
        return parts.joinToString(" & ")
    }

    private fun renderType(variable: NuxmvVariable): String {
        return when (variable.kind) {
            NuxmvVariableKind.Integer -> "integer"
            NuxmvVariableKind.Boolean -> "boolean"
            NuxmvVariableKind.Enum -> {
                val enum = variable.enumDeclaration
                    ?: error("Enum variable ${variable.name} has no enum declaration")
                enum.literals.joinToString(", ", prefix = "{ ", postfix = " }") { it.name }
            }
        }
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
}
