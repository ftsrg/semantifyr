/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler.pipeline.optimization.analyses

import hu.bme.mit.semantifyr.compiler.pipeline.utils.eAllOfType
import hu.bme.mit.semantifyr.oxsts.model.oxsts.AssignmentOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ComparisonOperator
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ElementReference
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Expression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.HavocOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlinedOxsts
import hu.bme.mit.semantifyr.oxsts.model.oxsts.PropertyDeclaration
import hu.bme.mit.semantifyr.oxsts.model.oxsts.TransitionDeclaration
import hu.bme.mit.semantifyr.oxsts.model.oxsts.TransitionKind
import hu.bme.mit.semantifyr.oxsts.model.oxsts.VariableDeclaration
import org.assertj.core.api.Assertions.assertThat
import org.eclipse.emf.ecore.EObject
import org.junit.jupiter.api.Test

/**
 * These tests document assumptions about OXSTS semantics that the analyses
 * rely on. Each fixture is short and each assertion is a single predicate,
 * so the test name + the fixture tells you exactly what I believe the model
 * means. If any assertion reads as semantically wrong, that is a concrete
 * pointer to where the analysis (or my mental model) diverges from OXSTS.
 *
 * Conventions used in the fixtures:
 *  - `inlined oxsts of semantifyr::Anything` — builtin stub class.
 *  - Only top-level variables; no nested instances.
 *  - `init { ... }` runs exactly once before the first state-point.
 *  - `tran { ... }` is the main transition, iterated an unbounded number of
 *    times. Its choice branches model nondeterminism.
 *  - The property is evaluated at every reachable state. My current
 *    understanding: "reachable state" means post-init, and each subsequent
 *    state is reached via one more main-tran step.
 */
class SemanticsEdgeCaseTest : AnalysisTestBase() {

    // ------------------------------------------------------------------
    // Variable declarations as a reaching "def"
    // ------------------------------------------------------------------

    @Test
    fun `non-local variable declaration is NOT a reaching def for property reads`() {
        // After init runs, the property only observes post-init state. The
        // declared initializer value of a non-local variable is not
        // observable from any property read unless init leaves the variable
        // untouched (that case is substituted by VariableLivenessPass's
        // init-only rule, not produced here).
        val run = runAnalysis(
            source = """
                inlined oxsts of semantifyr::Anything
                var a : int := 0
                init { a := 1 }
                tran { }
                prop { AG (a != 27) }
            """,
            analysisClass = ReachingDefinitionsAnalysis::class.java,
        )
        val a = run.inlinedOxsts.varNamed("a")
        val propRead = run.inlinedOxsts.findPropertyReadOf(a)
        assertThat(run.result.defsOf[propRead]!!).doesNotContain(a as EObject)
    }

    @Test
    fun `declaration without initializer is currently NOT a reaching def`() {
        // This documents current behaviour: my fix only adds the declaration
        // to initialIn when expression != null. A variable without an
        // initializer has non-deterministic initial value in most model-
        // checking semantics - if OXSTS treats it that way, this should
        // change so that CopyPropagation does not treat a lone assignment
        // as the unique reaching def.
        val run = runAnalysis(
            source = """
                inlined oxsts of semantifyr::Anything
                var a : int
                init { }
                tran { a := 5 }
                prop { AG (a != 27) }
            """,
            analysisClass = ReachingDefinitionsAnalysis::class.java,
        )
        val a = run.inlinedOxsts.varNamed("a")
        val propRead = run.inlinedOxsts.findPropertyReadOf(a)
        val defs = run.result.defsOf[propRead]!!
        val aWrite = run.inlinedOxsts.assignmentsTo(a).single()
        // Under my current model: only the tran write reaches.
        assertThat(defs).containsExactly(aWrite as EObject)
    }

    // ------------------------------------------------------------------
    // Havoc semantics
    // ------------------------------------------------------------------

    @Test
    fun `havoc kills prior defs within the same sequence`() {
        // Assumption: havoc overwrites the variable with a non-deterministic
        // value, so a subsequent read sees only the havoc as its reaching
        // def - the earlier assignment is killed.
        val run = runAnalysis(
            source = """
                inlined oxsts of semantifyr::Anything
                var a : int := 0
                var b : int := 0
                init { }
                tran {
                    a := 1
                    havoc(a)
                    b := a
                }
                prop { AG true }
            """,
            analysisClass = ReachingDefinitionsAnalysis::class.java,
        )
        val a = run.inlinedOxsts.varNamed("a")
        val havoc = run.inlinedOxsts.havocsOn(a).single()
        val bWrite = run.inlinedOxsts.assignmentsTo(run.inlinedOxsts.varNamed("b")).single()
        val aReadInBRhs = bWrite.expression as ElementReference

        assertThat(run.result.defsOf[aReadInBRhs]!!).containsExactly(havoc as EObject)
    }

    @Test
    fun `havoc makes a variable non-constant even if all assignments agree`() {
        val run = runAnalysis(
            source = """
                inlined oxsts of semantifyr::Anything
                var a : int := 7
                init { a := 7 }
                tran {
                    havoc(a)
                    a := 7
                }
                prop { AG (a == 7) }
            """,
            analysisClass = ConstantValueAnalysis::class.java,
        )
        val a = run.inlinedOxsts.varNamed("a")
        assertThat(run.result.isConstant(a)).isFalse
    }

    // ------------------------------------------------------------------
    // Control flow
    // ------------------------------------------------------------------

    @Test
    fun `assume does not kill defs - the walker treats it as a no-op for RD`() {
        // Assumption: `assume(...)` constrains reachability but doesn't
        // modify any variable. The reaching defs after the assume are the
        // same as before it.
        val run = runAnalysis(
            source = """
                inlined oxsts of semantifyr::Anything
                var a : int := 0
                var b : int := 0
                init { }
                tran {
                    a := 5
                    assume(a == 5)
                    b := a
                }
                prop { AG true }
            """,
            analysisClass = ReachingDefinitionsAnalysis::class.java,
        )
        val a = run.inlinedOxsts.varNamed("a")
        val aWrite = run.inlinedOxsts.assignmentsTo(a).single()
        val bWrite = run.inlinedOxsts.assignmentsTo(run.inlinedOxsts.varNamed("b")).single()
        val aReadInBRhs = bWrite.expression as ElementReference

        assertThat(run.result.defsOf[aReadInBRhs]!!).containsExactly(aWrite as EObject)
    }

    @Test
    fun `assume false does NOT prune subsequent writes from RD`() {
        // Documents current behaviour. A branch containing `assume(false)` is
        // unreachable at runtime, but the RD walker treats `assume` uniformly
        // as a no-op. Subsequent writes still appear in reaching-def sets.
        // Pruning such writes is done separately by ConstantAssumptionPropagationPass
        // BEFORE RD runs, so this shouldn't matter in practice - but if it
        // does, this test will catch the divergence.
        val run = runAnalysis(
            source = """
                inlined oxsts of semantifyr::Anything
                var a : int := 0
                init { }
                tran {
                    assume(false)
                    a := 5
                }
                prop { AG (a != 5) }
            """,
            analysisClass = ReachingDefinitionsAnalysis::class.java,
        )
        val a = run.inlinedOxsts.varNamed("a")
        val write = run.inlinedOxsts.assignmentsTo(a).single()
        val propRead = run.inlinedOxsts.findPropertyReadOf(a)
        // Under current model: the write is considered a reaching def for the
        // property even though it is unreachable.
        assertThat(run.result.defsOf[propRead]!!).contains(write as EObject)
    }

    @Test
    fun `choice branches are joined - OUT is the union of branch OUTs`() {
        val run = runAnalysis(
            source = """
                inlined oxsts of semantifyr::Anything
                var a : int := 0
                var b : int := 0
                init { }
                tran {
                    choice { a := 1 } or { a := 2 }
                    b := a
                }
                prop { AG true }
            """,
            analysisClass = ReachingDefinitionsAnalysis::class.java,
        )
        val a = run.inlinedOxsts.varNamed("a")
        val aWrites = run.inlinedOxsts.assignmentsTo(a).toSet()
        val bWrite = run.inlinedOxsts.assignmentsTo(run.inlinedOxsts.varNamed("b")).single()
        val aReadInBRhs = bWrite.expression as ElementReference
        val defs = run.result.defsOf[aReadInBRhs]!!

        assertThat(defs)
            .`as`("after a choice, both branches' writes reach the downstream read")
            .containsAll(aWrites.map { it as EObject })
    }

    @Test
    fun `if-without-else passes through the original defs on the false arm`() {
        // Assumption: `if (g) { ... }` with no else has OUT = bodyOut U incoming.
        // A downstream read sees writes from the body AND writes that were
        // already reaching before the `if`.
        val run = runAnalysis(
            source = """
                inlined oxsts of semantifyr::Anything
                var a : int := 0
                var b : int := 0
                init { }
                tran {
                    a := 1
                    if (b == 0) { a := 2 }
                    b := a
                }
                prop { AG true }
            """,
            analysisClass = ReachingDefinitionsAnalysis::class.java,
        )
        val a = run.inlinedOxsts.varNamed("a")
        val allAWrites = run.inlinedOxsts.assignmentsTo(a).toSet()
        val bWrite = run.inlinedOxsts.assignmentsTo(run.inlinedOxsts.varNamed("b")).single()
        val aReadInBRhs = bWrite.expression as ElementReference
        val defs = run.result.defsOf[aReadInBRhs]!!
        assertThat(defs).containsAll(allAWrites.map { it as EObject })
    }

    // ------------------------------------------------------------------
    // Init vs main semantics
    // ------------------------------------------------------------------

    @Test
    fun `reads in init currently see main-tran writes - sound but imprecise`() {
        // Each transition is walked with initialIn = all writes. Reads inside
        // init therefore see main-tran writes too, even though init runs
        // before main so main writes cannot really reach init. This is
        // over-approximation; consumers only act on singletons and the extra
        // defs only widen the set.
        val run = runAnalysis(
            source = """
                inlined oxsts of semantifyr::Anything
                var a : int := 0
                var b : int := 0
                init {
                    a := b
                }
                tran {
                    b := 99
                }
                prop { AG true }
            """,
            analysisClass = ReachingDefinitionsAnalysis::class.java,
        )
        val b = run.inlinedOxsts.varNamed("b")
        val initRead = run.inlinedOxsts.readsOfInInit(b).single()
        val bWriteInTran = run.inlinedOxsts.assignmentsTo(b).single()

        val defs = run.result.defsOf[initRead]!!
        // Current analysis includes writes from both transitions.
        assertThat(defs).contains(bWriteInTran as EObject)
    }

    @Test
    fun `main-tran entry sees all writes including those that appear later in the tran body`() {
        // initialIn for main tran includes all writes anywhere. A read at the
        // start of main tran sees writes that only execute later in the tran
        // body - this models the loop back-edge (iteration N+1 sees
        // iteration N's writes).
        val run = runAnalysis(
            source = """
                inlined oxsts of semantifyr::Anything
                var a : int := 0
                var b : int := 0
                init { }
                tran {
                    b := a
                    a := 5
                }
                prop { AG true }
            """,
            analysisClass = ReachingDefinitionsAnalysis::class.java,
        )
        val a = run.inlinedOxsts.varNamed("a")
        val bWrite = run.inlinedOxsts.assignmentsTo(run.inlinedOxsts.varNamed("b")).single()
        val aReadInBRhs = bWrite.expression as ElementReference
        val aWrite = run.inlinedOxsts.assignmentsTo(a).single()

        val defs = run.result.defsOf[aReadInBRhs]!!
        // The later write reaches via loop back-edge approximation.
        assertThat(defs).contains(aWrite as EObject)
        // But the declaration itself is NOT a def.
        assertThat(defs).doesNotContain(a as EObject)
    }

    // ------------------------------------------------------------------
    // Cone of influence - edge cases
    // ------------------------------------------------------------------

    @Test
    fun `cone excludes writes to a variable that is never read anywhere`() {
        val run = runAnalysis(
            source = """
                inlined oxsts of semantifyr::Anything
                var a : int := 0
                var b : int := 0
                init { }
                tran { b := 1 }
                prop { AG (a == 0) }
            """,
            analysisClass = ConeOfInfluenceAnalysis::class.java,
        )
        val b = run.inlinedOxsts.varNamed("b")
        val bWrite = run.inlinedOxsts.assignmentsTo(b).single()
        assertThat(run.result.isRelevant(b)).isFalse
        assertThat(run.result.isRelevant(bWrite)).isFalse
    }

    @Test
    fun `nested if guards contribute variables to the cone`() {
        // A write is inside nested guards. The guardVariablesFor walk should
        // collect variables from every ancestor if/assume. Documents that
        // behaviour.
        val run = runAnalysis(
            source = """
                inlined oxsts of semantifyr::Anything
                var a : int := 0
                var b : int := 0
                var c : int := 0
                init { }
                tran {
                    if (b == 0) {
                        if (c == 0) {
                            a := 1
                        }
                    }
                }
                prop { AG (a != 1) }
            """,
            analysisClass = ConeOfInfluenceAnalysis::class.java,
        )
        val a = run.inlinedOxsts.varNamed("a")
        val b = run.inlinedOxsts.varNamed("b")
        val c = run.inlinedOxsts.varNamed("c")
        assertThat(run.result.isRelevant(a)).isTrue
        assertThat(run.result.isRelevant(b))
            .`as`("outer if-guard variable 'b' should be in the cone")
            .isTrue
        assertThat(run.result.isRelevant(c))
            .`as`("inner if-guard variable 'c' should be in the cone")
            .isTrue
    }

    @Test
    fun `havoc to a relevant variable is itself relevant`() {
        val run = runAnalysis(
            source = """
                inlined oxsts of semantifyr::Anything
                var a : int := 0
                init { }
                tran { havoc(a) }
                prop { AG (a != 42) }
            """,
            analysisClass = ConeOfInfluenceAnalysis::class.java,
        )
        val a = run.inlinedOxsts.varNamed("a")
        val havoc = run.inlinedOxsts.havocsOn(a).single()
        assertThat(run.result.isRelevant(havoc))
            .`as`("havoc on a property-relevant variable must be kept")
            .isTrue
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private fun InlinedOxsts.varNamed(name: String): VariableDeclaration {
        return eAllOfType<VariableDeclaration>().firstOrNull { it.name == name }
            ?: error("No variable named '$name'")
    }

    private fun InlinedOxsts.assignmentsTo(variable: VariableDeclaration): List<AssignmentOperation> {
        return eAllOfType<AssignmentOperation>().filter { op ->
            val ref = op.reference
            ref is ElementReference && ref.element === variable
        }.toList()
    }

    private fun InlinedOxsts.havocsOn(variable: VariableDeclaration): List<HavocOperation> {
        return eAllOfType<HavocOperation>().filter { op ->
            val ref = op.reference
            ref is ElementReference && ref.element === variable
        }.toList()
    }

    private fun InlinedOxsts.readsOfInInit(variable: VariableDeclaration): List<Expression> {
        val init = eAllOfType<TransitionDeclaration>().first { it.kind == TransitionKind.INIT }
        return init.eAllOfType<ElementReference>()
            .filter { it.element === variable }
            .filterNot { ref ->
                val parent = ref.eContainer()
                parent is AssignmentOperation && parent.reference === ref
            }
            .filterNot { ref ->
                val parent = ref.eContainer()
                parent is HavocOperation && parent.reference === ref
            }
            .toList()
    }

    private fun InlinedOxsts.findPropertyReadOf(variable: VariableDeclaration): Expression {
        val property = eAllOfType<PropertyDeclaration>().first()
        return property.expression.eAllOfType<ElementReference>()
            .firstOrNull { it.element === variable }
            ?: error("Property does not reference '${variable.name}'")
    }
}
