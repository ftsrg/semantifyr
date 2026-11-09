/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler.pipeline.utils

import com.google.inject.Inject
import hu.bme.mit.semantifyr.oxsts.lang.semantics.expression.ConstantExpressionEvaluatorProvider
import hu.bme.mit.semantifyr.oxsts.lang.semantics.expression.IntegerEvaluation
import hu.bme.mit.semantifyr.oxsts.lang.tests.InjectWithOxsts
import hu.bme.mit.semantifyr.oxsts.lang.tests.utils.InlinedOxstsParseHelper
import org.assertj.core.api.Assertions.assertThat
import org.eclipse.xtext.serializer.ISerializer
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

@InjectWithOxsts
class OxstsFactoryTest {

    @Inject
    private lateinit var parseHelper: InlinedOxstsParseHelper

    @Inject
    private lateinit var serializer: ISerializer

    @Inject
    private lateinit var evaluatorProvider: ConstantExpressionEvaluatorProvider

    @ParameterizedTest
    @ValueSource(
        ints = [
            0,
            7,
            -7,
            Int.MAX_VALUE,
        ],
    )
    fun `an integer literal serializes to text that reparses to its value`(value: Int) {
        val model = parseHelper.parse(
            """
                inlined oxsts of semantifyr::Anything
                var value: int := 0
                init {
                }
                tran {
                }
                prop {
                    AG true
                }
            """.trimIndent(),
        )
        model.variables.single().expression = OxstsFactory.createLiteralInteger(value)

        val reparsed = parseHelper.parse(serializer.serialize(model))

        assertThat(reparsed.eResource().errors).isEmpty()
        assertThat(evaluatorProvider.evaluate(reparsed.variables.single().expression)).isEqualTo(IntegerEvaluation(value))
    }
}
