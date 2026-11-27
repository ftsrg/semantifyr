/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler.pipeline.flattening

import hu.bme.mit.semantifyr.compiler.pipeline.context.FlattenedCompilationContext
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.OptimizationConfig
import hu.bme.mit.semantifyr.oxsts.model.oxsts.DataTypeDeclaration
import hu.bme.mit.semantifyr.oxsts.model.oxsts.EnumDeclaration
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class FlattenedModelInvariantsTest : FlatteningTestBase() {

    private val host = """
        package flattening::invariants

        enum Mode {
            Idle,
            Busy
        }

        class Job {
            var done: bool := false

            tran finish() {
                done := true
            }
        }

        @VerificationCase
        class Host {
            contains first: Job
            contains second: Job
            var current: Job := first
            var mode: Mode := Mode::Idle
            var unused: int := 0

            redefine tran {
                choice {
                    current := second
                } or {
                    mode := Mode::Busy
                }
                inline current.finish()
            }

            redefine prop {
                return EF (mode == Mode::Busy && second.done)
            }
        }
    """

    private fun flattenHost(): FlattenedCompilationContext {
        return flatten("Host", host, optimization = OptimizationConfig.ALL)
    }

    @Test
    fun `a flattened model has no root feature`() {
        assertThat(flattenHost().inlinedOxsts.rootFeature).isNull()
    }

    @Test
    fun `every variable of a flattened model has a data type or an enum as its domain`() {
        val variables = flattenHost().inlinedOxsts.variables

        assertThat(
            variables.map {
                it.name
            },
        ).anyMatch {
            it.endsWith("current")
        }
        assertThat(variables).allSatisfy {
            assertThat(it.typeSpecification.domain).isInstanceOfAny(DataTypeDeclaration::class.java, EnumDeclaration::class.java)
        }
    }

    @Test
    fun `every variable holder of a flattened model is a variable the model keeps`() {
        val flattened = flattenHost()

        assertThat(
            flattened.inlinedOxsts.variables.map {
                it.name
            },
        ).noneMatch {
            it.endsWith("unused")
        }
        assertThat(flattened.flatteningInfo.variableHolders.keys).isSubsetOf(flattened.inlinedOxsts.variables)
    }
}
