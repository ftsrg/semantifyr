/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler.pipeline.flattening

import hu.bme.mit.semantifyr.compiler.pipeline.context.FlattenedCompilationContext
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.OptimizationConfig
import hu.bme.mit.semantifyr.oxsts.lang.tests.utils.TestModels.testModel
import hu.bme.mit.semantifyr.oxsts.model.oxsts.DataTypeDeclaration
import hu.bme.mit.semantifyr.oxsts.model.oxsts.EnumDeclaration
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class FlattenedModelInvariantsTest : FlatteningTestBase() {

    private val host = testModel(javaClass, "host.semantifyr")

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
