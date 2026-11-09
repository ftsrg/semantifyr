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
    lateinit var temporaryDirectory: Path

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
                var values : int[3] := [10, 20, 30]
                init {
                }
                tran {
                }
                prop {
                    AG true
                }
            """.trimIndent(),
        )

        val flattened = flattenArrays(inlined)

        val names = inlined.variables.map {
            it.name
        }
        assertThat(names).containsExactly("values_0", "values_1", "values_2")
        assertThat(
            flattened.keys.map {
                it.name
            },
        ).containsExactly("values")
        assertThat(flattened.values.single()).containsExactlyElementsOf(inlined.variables)
    }

    @Test
    fun `array literal element initializers are split across slot variables`() {
        val inlined = parseHelper.parse(
            """
                inlined oxsts of semantifyr::Anything
                var values : int[3] := [10, 20, 30]
                init {
                }
                tran {
                }
                prop {
                    AG true
                }
            """.trimIndent(),
        )

        flattenArrays(inlined)

        val serialized = serialize(inlined)
        assertThat(serialized).contains("values_0 : int := 10")
        assertThat(serialized).contains("values_1 : int := 20")
        assertThat(serialized).contains("values_2 : int := 30")
    }

    @Test
    fun `constant-index reads are rewritten to the matching slot variable`() {
        val inlined = parseHelper.parse(
            """
                inlined oxsts of semantifyr::Anything
                var values : int[3] := [10, 20, 30]
                var result : int := 0
                init {
                }
                tran {
                    result := values[1]
                }
                prop {
                    AG true
                }
            """.trimIndent(),
        )

        flattenArrays(inlined)

        assertThat(inlined.eAllOfType<IndexingSuffixExpression>().toList())
            .`as`("all indexings on the array variable should have been rewritten")
            .isEmpty()
        assertThat(serialize(inlined)).contains("result := values_1")
    }

    @Test
    fun `constant-index writes are rewritten to the matching slot variable`() {
        val inlined = parseHelper.parse(
            """
                inlined oxsts of semantifyr::Anything
                var values : int[3] := [0, 0, 0]
                init {
                }
                tran {
                    values[2] := 42
                }
                prop {
                    AG true
                }
            """.trimIndent(),
        )

        flattenArrays(inlined)

        assertThat(inlined.eAllOfType<IndexingSuffixExpression>().toList()).isEmpty()
        assertThat(serialize(inlined)).contains("values_2 := 42")
    }

    @Test
    fun `scalar variables are not flattened`() {
        val inlined = parseHelper.parse(
            """
                inlined oxsts of semantifyr::Anything
                var scalar : int := 0
                init {
                }
                tran {
                    scalar := 1
                }
                prop {
                    AG true
                }
            """.trimIndent(),
        )

        val flattened = flattenArrays(inlined)

        assertThat(flattened).isEmpty()
        assertThat(
            inlined.variables.map {
                it.name
            },
        ).containsExactly("scalar")
    }

    @Test
    fun `runtime-index read rewrites to an if-then-else chain over slot variables`() {
        val inlined = parseHelper.parse(
            """
                inlined oxsts of semantifyr::Anything
                var values : int[3] := [10, 20, 30]
                var index : int := 0
                var seen : int := 0
                init {
                }
                tran {
                    seen := values[index]
                }
                prop {
                    AG true
                }
            """.trimIndent(),
        )

        flattenArrays(inlined)

        assertThat(inlined.eAllOfType<IndexingSuffixExpression>().toList())
            .`as`("runtime-index read should be rewritten - no IndexingSuffixExpression left")
            .isEmpty()
        val serialized = serialize(inlined)

        assertThat(serialized).contains("values_0")
        assertThat(serialized).contains("values_1")
        assertThat(serialized).contains("values_2")
        assertThat(serialized).contains("if")
        assertThat(serialized).contains("then")
        assertThat(serialized).contains("else")
    }

    @Test
    fun `runtime-index write rewrites the enclosing assignment to an if-chain over slot writes`() {
        val inlined = parseHelper.parse(
            """
                inlined oxsts of semantifyr::Anything
                var values : int[3] := [0, 0, 0]
                var index : int := 0
                init {
                }
                tran {
                    values[index] := 42
                }
                prop {
                    AG true
                }
            """.trimIndent(),
        )

        flattenArrays(inlined)

        assertThat(inlined.eAllOfType<IndexingSuffixExpression>().toList())
            .`as`("runtime-index write should have been rewritten to an if-chain")
            .isEmpty()
        val serialized = serialize(inlined)
        assertThat(serialized).contains("values_0 := 42")
        assertThat(serialized).contains("values_1 := 42")
        assertThat(serialized).contains("values_2 := 42")
        assertThat(serialized).contains("if")
    }

    @Test
    fun `local array is split into per-index local declarations in place`() {
        val inlined = parseHelper.parse(
            """
                inlined oxsts of semantifyr::Anything
                var result : int := 0
                var index : int := 0
                init {
                }
                tran {
                    var values : int[3] := [1, 2, result]
                    result := values[index]
                    values[2] := values[0]
                }
                prop {
                    AG true
                }
            """.trimIndent(),
        )

        flattenArrays(inlined)

        assertThat(inlined.eAllOfType<IndexingSuffixExpression>().toList()).isEmpty()
        assertThat(
            inlined.variables.map {
                it.name
            },
        ).containsExactly("result", "index")
        val serialized = serialize(inlined)
        assertThat(serialized).contains("var values_0 : int := 1", "var values_1 : int := 2", "var values_2 : int := result", "values_2 := values_0")
        assertThat(serialized).containsPattern("result := if index == 0 then values_0 else if index == 1 then values_1 else values_2")
    }

    @Test
    fun `whole-array assignment, havoc and comparison work slot by slot`() {
        val inlined = parseHelper.parse(
            """
                inlined oxsts of semantifyr::Anything
                var values : int[2] := [1, 2]
                var others : int[2] := [3, 4]
                var same : bool := false
                init {
                }
                tran {
                    values := others
                    others := [5, 6]
                    havoc(values)
                    same := values == others
                }
                prop {
                    AG values != others
                }
            """.trimIndent(),
        )

        flattenArrays(inlined)

        val serialized = serialize(inlined)
        assertThat(serialized).contains("values_0 := others_0", "values_1 := others_1", "others_0 := 5", "others_1 := 6", "havoc (values_0)", "havoc (values_1)")
        assertThat(serialized).contains("same := values_0 == others_0 && values_1 == others_1", "AG values_0 != others_0 || values_1 != others_1")
    }

    @Test
    fun `local array initialized from another array copies every slot`() {
        val inlined = parseHelper.parse(
            """
                inlined oxsts of semantifyr::Anything
                var values : int[2] := [1, 2]
                var result : int := 0
                init {
                }
                tran {
                    var copy : int[2] := values
                    values[0] := 9
                    result := copy[0]
                }
                prop {
                    AG true
                }
            """.trimIndent(),
        )

        flattenArrays(inlined)

        val serialized = serialize(inlined)
        assertThat(serialized).contains("var copy_0 : int := values_0", "var copy_1 : int := values_1", "values_0 := 9", "result := copy_0")
    }

    @Test
    fun `out-of-bounds constant index is rejected`() {
        val inlined = parseHelper.parse(
            """
                inlined oxsts of semantifyr::Anything
                var values : int[3] := [0, 0, 0]
                init {
                }
                tran {
                    values[5] := 1
                }
                prop {
                    AG true
                }
            """.trimIndent(),
        )

        assertThatThrownBy {
            flattenArrays(inlined)
        }.hasMessageContaining("out of bounds")
    }

    @Test
    fun `array-literal with fewer values than slots is rejected`() {
        val inlined = parseHelper.parse(
            """
                inlined oxsts of semantifyr::Anything
                var values : int[4] := [10, 20]
                init {
                }
                tran {
                }
                prop {
                    AG true
                }
            """.trimIndent(),
        )

        assertThatThrownBy {
            flattenArrays(inlined)
        }.hasMessageContaining("slots")
    }

    @Test
    fun `array-literal with too many values is rejected`() {
        val inlined = parseHelper.parse(
            """
                inlined oxsts of semantifyr::Anything
                var values : int[2] := [1, 2, 3]
                init {
                }
                tran {
                }
                prop {
                    AG true
                }
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
                init {
                }
                tran {
                }
                prop {
                    AG true
                }
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
                var values : int[2] := [1, 2]
                var others : int[2] := [3, 4]
                init {
                }
                tran {
                    values := [values[1], values[0]]
                    others := [if others[0] > 0 then values[0] else others[0], if others[0] > 0 then values[1] else others[1]]
                }
                prop {
                    AG true
                }
            """.trimIndent(),
        )

        flattenArrays(inlined)

        val serialized = serialize(inlined)
        assertThat(serialized).containsPattern("var ${saved("values_1")} : int := values_0\\s+values_0 := values_1\\s+values_1 := ${saved("values_1")}")
        assertThat(serialized).containsPattern("var ${saved("others_1")} : int := if others_0 > 0 then values_1 else others_1\\s+others_0 := if others_0 > 0 then values_0 else others_0\\s+others_1 := ${saved("others_1")}")
    }

    @Test
    fun `a whole-array value is saved only when it may read a slot written before it`() {
        val inlined = parseHelper.parse(
            """
                inlined oxsts of semantifyr::Anything
                var values : int[2] := [1, 2]
                var others : int[2] := [3, 4]
                var index : int := 0
                init {
                }
                tran {
                    values := [values[0] + 1, values[1] + 1]
                    others := [0, others[index]]
                }
                prop {
                    AG true
                }
            """.trimIndent(),
        )

        flattenArrays(inlined)

        val serialized = serialize(inlined)
        assertThat(serialized).contains("values_0 := values_0 + 1", "values_1 := values_1 + 1")
        assertThat(serialized).doesNotContainPattern(saved("values_\\d"))
        assertThat(serialized).containsPattern("var ${saved("others_1")} : int := if index == 0 then others_0 else others_1\\s+others_0 := 0\\s+others_1 := ${saved("others_1")}")
    }

    private fun saved(slot: String): String {
        return "'$slot${Regex.escape(LocalVarNames.LOCAL_VAR_SUFFIX)}\\d+'"
    }

    private fun flattenArrays(inlined: InlinedOxsts): Map<VariableDeclaration, List<VariableDeclaration>> {
        val child = injector.createChildInjector(CompilationModule(ArtifactConfig.NONE, OptimizationConfig.NONE))
        return withCompilationScopeBlocking(CompilationRequest(inlined, temporaryDirectory)) {
            child.getInstance(ArrayFlatteningPass::class.java).flattenArrays(inlined)
        }
    }

    private fun serialize(model: InlinedOxsts): String {
        return serializer.serializeFormatted(model)
    }
}
