/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler.pipeline.flattening

import com.google.inject.Inject
import com.google.inject.Injector
import hu.bme.mit.semantifyr.compiler.pipeline.CompilationModule
import hu.bme.mit.semantifyr.compiler.pipeline.CompilationRequest
import hu.bme.mit.semantifyr.compiler.pipeline.artifact.ArtifactConfig
import hu.bme.mit.semantifyr.compiler.pipeline.inlining.LocalVarNames
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.OptimizationConfig
import hu.bme.mit.semantifyr.compiler.pipeline.utils.eAllOfType
import hu.bme.mit.semantifyr.compiler.pipeline.utils.serializeFormatted
import hu.bme.mit.semantifyr.compiler.scopes.withCompilationScopeBlocking
import hu.bme.mit.semantifyr.oxsts.lang.tests.InjectWithOxsts
import hu.bme.mit.semantifyr.oxsts.lang.tests.utils.InlinedOxstsParseHelper
import hu.bme.mit.semantifyr.oxsts.model.oxsts.IndexingSuffixExpression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlinedOxsts
import hu.bme.mit.semantifyr.oxsts.model.oxsts.VariableDeclaration
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.eclipse.xtext.serializer.ISerializer
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

@InjectWithOxsts
class ArrayFlatteningPassTest {
    @TempDir
    lateinit var tempDir: Path

    @Inject
    private lateinit var parseHelper: InlinedOxstsParseHelper

    @Inject
    private lateinit var injector: Injector

    @Inject
    private lateinit var serializer: ISerializer

    @Test
    fun `fixed-size int array is split into per-index variables`() {
        val inlined = parseHelper.parse(
            """
                inlined oxsts of semantifyr::Anything
                var a : int[3] := [10, 20, 30]
                init { }
                tran { }
                prop { AG true }
            """.trimIndent(),
        )

        val flattened = flattenArrays(inlined)

        val names = inlined.variables.map {
            it.name
        }
        assertThat(names).containsExactly("a_0", "a_1", "a_2")
        assertThat(flattened.keys.map { it.name }).containsExactly("a")
        assertThat(flattened.values.single()).containsExactlyElementsOf(inlined.variables)
    }

    @Test
    fun `array literal element initializers are split across slot variables`() {
        val inlined = parseHelper.parse(
            """
                inlined oxsts of semantifyr::Anything
                var a : int[3] := [10, 20, 30]
                init { }
                tran { }
                prop { AG true }
            """.trimIndent(),
        )

        flattenArrays(inlined)

        val serialized = serialize(inlined)
        assertThat(serialized).contains("a_0 : int := 10")
        assertThat(serialized).contains("a_1 : int := 20")
        assertThat(serialized).contains("a_2 : int := 30")
    }

    @Test
    fun `array without initializer produces uninitialized slot variables`() {
        val inlined = parseHelper.parse(
            """
                inlined oxsts of semantifyr::Anything
                var a : int[2]
                init { }
                tran { }
                prop { AG true }
            """.trimIndent(),
        )

        flattenArrays(inlined)

        val slots = inlined.variables.filter {
            it.name.startsWith("a_")
        }
        assertThat(slots).hasSize(2)
        assertThat(slots.all { it.expression == null }).isTrue()
    }

    @Test
    fun `constant-index reads are rewritten to the matching slot variable`() {
        val inlined = parseHelper.parse(
            """
                inlined oxsts of semantifyr::Anything
                var a : int[3] := [10, 20, 30]
                var result : int := 0
                init { }
                tran { result := a[1] }
                prop { AG true }
            """.trimIndent(),
        )

        flattenArrays(inlined)

        assertThat(inlined.eAllOfType<IndexingSuffixExpression>().toList())
            .`as`("all indexings on the array variable should have been rewritten")
            .isEmpty()
        assertThat(serialize(inlined)).contains("result := a_1")
    }

    @Test
    fun `constant-index writes are rewritten to the matching slot variable`() {
        val inlined = parseHelper.parse(
            """
                inlined oxsts of semantifyr::Anything
                var a : int[3] := [0, 0, 0]
                init { }
                tran { a[2] := 42 }
                prop { AG true }
            """.trimIndent(),
        )

        flattenArrays(inlined)

        assertThat(inlined.eAllOfType<IndexingSuffixExpression>().toList()).isEmpty()
        assertThat(serialize(inlined)).contains("a_2 := 42")
    }

    @Test
    fun `scalar variables are not flattened`() {
        val inlined = parseHelper.parse(
            """
                inlined oxsts of semantifyr::Anything
                var x : int := 0
                init { }
                tran { x := 1 }
                prop { AG true }
            """.trimIndent(),
        )

        val flattened = flattenArrays(inlined)

        assertThat(flattened).isEmpty()
        assertThat(inlined.variables.map { it.name }).containsExactly("x")
    }

    @Test
    fun `runtime-index read rewrites to an if-then-else chain over slot variables`() {
        val inlined = parseHelper.parse(
            """
                inlined oxsts of semantifyr::Anything
                var a : int[3] := [10, 20, 30]
                var idx : int := 0
                var seen : int := 0
                init { }
                tran { seen := a[idx] }
                prop { AG true }
            """.trimIndent(),
        )

        flattenArrays(inlined)

        assertThat(inlined.eAllOfType<IndexingSuffixExpression>().toList())
            .`as`("runtime-index read should be rewritten - no IndexingSuffixExpression left")
            .isEmpty()
        val serialized = serialize(inlined)

        assertThat(serialized).contains("a_0")
        assertThat(serialized).contains("a_1")
        assertThat(serialized).contains("a_2")
        assertThat(serialized).contains("if")
        assertThat(serialized).contains("then")
        assertThat(serialized).contains("else")
    }

    @Test
    fun `runtime-index write rewrites the enclosing assignment to an if-chain over slot writes`() {
        val inlined = parseHelper.parse(
            """
                inlined oxsts of semantifyr::Anything
                var a : int[3] := [0, 0, 0]
                var idx : int := 0
                init { }
                tran { a[idx] := 42 }
                prop { AG true }
            """.trimIndent(),
        )

        flattenArrays(inlined)

        assertThat(inlined.eAllOfType<IndexingSuffixExpression>().toList())
            .`as`("runtime-index write should have been rewritten to an if-chain")
            .isEmpty()
        val serialized = serialize(inlined)
        assertThat(serialized).contains("a_0 := 42")
        assertThat(serialized).contains("a_1 := 42")
        assertThat(serialized).contains("a_2 := 42")
        assertThat(serialized).contains("if")
    }

    @Test
    fun `local array is split into per-index local declarations in place`() {
        val inlined = parseHelper.parse(
            """
                inlined oxsts of semantifyr::Anything
                var x : int := 0
                var i : int := 0
                init { }
                tran {
                    var a : int[3] := [1, 2, x]
                    x := a[i]
                    a[2] := a[0]
                }
                prop { AG true }
            """.trimIndent(),
        )

        flattenArrays(inlined)

        assertThat(inlined.eAllOfType<IndexingSuffixExpression>().toList()).isEmpty()
        assertThat(inlined.variables.map { it.name }).containsExactly("x", "i")
        val serialized = serialize(inlined)
        assertThat(serialized).contains("var a_0 : int := 1", "var a_1 : int := 2", "var a_2 : int := x", "a_2 := a_0")
        assertThat(serialized).containsPattern("x := if i == 0 then a_0 else if i == 1 then a_1 else a_2")
    }

    @Test
    fun `local array without initializer produces uninitialized local slots`() {
        val inlined = parseHelper.parse(
            """
                inlined oxsts of semantifyr::Anything
                var x : int := 0
                init { }
                tran {
                    var a : int[2]
                    x := a[1]
                }
                prop { AG true }
            """.trimIndent(),
        )

        flattenArrays(inlined)

        val serialized = serialize(inlined)
        assertThat(serialized).contains("var a_0 : int", "var a_1 : int", "x := a_1")
        assertThat(serialized).doesNotContain(":= [")
    }

    @Test
    fun `whole-array assignment, havoc and comparison work slot by slot`() {
        val inlined = parseHelper.parse(
            """
                inlined oxsts of semantifyr::Anything
                var a : int[2] := [1, 2]
                var b : int[2] := [3, 4]
                var same : bool := false
                init { }
                tran {
                    a := b
                    b := [5, 6]
                    havoc(a)
                    same := a == b
                }
                prop { AG a != b }
            """.trimIndent(),
        )

        flattenArrays(inlined)

        val serialized = serialize(inlined)
        assertThat(serialized).contains("a_0 := b_0", "a_1 := b_1", "b_0 := 5", "b_1 := 6", "havoc (a_0)", "havoc (a_1)")
        assertThat(serialized).contains("same := a_0 == b_0 && a_1 == b_1", "AG a_0 != b_0 || a_1 != b_1")
    }

    @Test
    fun `local array initialized from another array copies every slot`() {
        val inlined = parseHelper.parse(
            """
                inlined oxsts of semantifyr::Anything
                var a : int[2] := [1, 2]
                var x : int := 0
                init { }
                tran {
                    var copy : int[2] := a
                    a[0] := 9
                    x := copy[0]
                }
                prop { AG true }
            """.trimIndent(),
        )

        flattenArrays(inlined)

        val serialized = serialize(inlined)
        assertThat(serialized).contains("var copy_0 : int := a_0", "var copy_1 : int := a_1", "a_0 := 9", "x := copy_0")
    }

    @Test
    fun `out-of-bounds constant index is rejected`() {
        val inlined = parseHelper.parse(
            """
                inlined oxsts of semantifyr::Anything
                var a : int[3] := [0, 0, 0]
                init { }
                tran { a[5] := 1 }
                prop { AG true }
            """.trimIndent(),
        )

        assertThatThrownBy {
            flattenArrays(inlined)
        }.hasMessageContaining("out of bounds")
    }

    @Test
    fun `array-literal with fewer values than slots leaves the rest uninitialized`() {
        val inlined = parseHelper.parse(
            """
                inlined oxsts of semantifyr::Anything
                var a : int[4] := [10, 20]
                init { }
                tran { }
                prop { AG true }
            """.trimIndent(),
        )

        flattenArrays(inlined)

        val slots = inlined.variables.filter {
            it.name.startsWith("a_")
        }
        assertThat(slots).hasSize(4)
        assertThat(slots[0].expression).isNotNull
        assertThat(slots[1].expression).isNotNull
        assertThat(slots[2].expression).isNull()
        assertThat(slots[3].expression).isNull()
    }

    @Test
    fun `array-literal with too many values is rejected`() {
        val inlined = parseHelper.parse(
            """
                inlined oxsts of semantifyr::Anything
                var a : int[2] := [1, 2, 3]
                init { }
                tran { }
                prop { AG true }
            """.trimIndent(),
        )

        assertThatThrownBy {
            flattenArrays(inlined)
        }.hasMessageContaining("slots")
    }

    @Test
    fun `bool array is flattened slot-wise with element domain preserved`() {
        val inlined = parseHelper.parse(
            """
                inlined oxsts of semantifyr::Anything
                var flags : bool[2] := [true, false]
                init { }
                tran { }
                prop { AG true }
            """.trimIndent(),
        )

        flattenArrays(inlined)

        val slots = inlined.variables.filter {
            it.name.startsWith("flags_")
        }
        assertThat(slots).hasSize(2)
        assertThat(slots[0].typeSpecification?.domain?.name).isEqualTo("bool")
        assertThat(slots[1].typeSpecification?.domain?.name).isEqualTo("bool")
    }

    @Test
    fun `a whole-array value that reads a slot written before it is saved first`() {
        val inlined = parseHelper.parse(
            """
                inlined oxsts of semantifyr::Anything
                var a : int[2] := [1, 2]
                var b : int[2] := [3, 4]
                init { }
                tran {
                    a := [a[1], a[0]]
                    b := [if b[0] > 0 then a[0] else b[0], if b[0] > 0 then a[1] else b[1]]
                }
                prop { AG true }
            """.trimIndent(),
        )

        flattenArrays(inlined)

        val serialized = serialize(inlined)
        assertThat(serialized).containsPattern("var ${saved("a_1")} : int := a_0\\s+a_0 := a_1\\s+a_1 := ${saved("a_1")}")
        assertThat(serialized).containsPattern("var ${saved("b_1")} : int := if b_0 > 0 then a_1 else b_1\\s+b_0 := if b_0 > 0 then a_0 else b_0\\s+b_1 := ${saved("b_1")}")
    }

    @Test
    fun `a whole-array value is saved only when it may read a slot written before it`() {
        val inlined = parseHelper.parse(
            """
                inlined oxsts of semantifyr::Anything
                var a : int[2] := [1, 2]
                var b : int[2] := [3, 4]
                var i : int := 0
                init { }
                tran {
                    a := [a[0] + 1, a[1] + 1]
                    b := [0, b[i]]
                }
                prop { AG true }
            """.trimIndent(),
        )

        flattenArrays(inlined)

        val serialized = serialize(inlined)
        assertThat(serialized).contains("a_0 := a_0 + 1", "a_1 := a_1 + 1")
        assertThat(serialized).doesNotContainPattern(saved("a_\\d"))
        assertThat(serialized).containsPattern("var ${saved("b_1")} : int := if i == 0 then b_0 else b_1\\s+b_0 := 0\\s+b_1 := ${saved("b_1")}")
    }

    private fun saved(slot: String): String {
        return "'$slot${Regex.escape(LocalVarNames.LOCAL_VAR_SUFFIX)}\\d+'"
    }

    private fun flattenArrays(inlined: InlinedOxsts): Map<VariableDeclaration, List<VariableDeclaration>> {
        val child = injector.createChildInjector(CompilationModule(ArtifactConfig.NONE, OptimizationConfig.NONE))
        return withCompilationScopeBlocking(CompilationRequest(inlined, tempDir)) {
            child.getInstance(ArrayFlatteningPass::class.java).flattenArrays(inlined)
        }
    }

    private fun serialize(model: InlinedOxsts): String {
        return serializer.serializeFormatted(model)
    }
}
