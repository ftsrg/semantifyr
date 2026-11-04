/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.theta.backannotation

import com.google.inject.Inject
import com.google.inject.Injector
import hu.bme.mit.semantifyr.backend.BackendTestModels
import hu.bme.mit.semantifyr.backend.scopes.withVerificationScope
import hu.bme.mit.semantifyr.backends.theta.ThetaBackendModule
import hu.bme.mit.semantifyr.backends.theta.transformation.xsts.XstsFactory
import hu.bme.mit.semantifyr.oxsts.lang.tests.InjectWithOxsts
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ArithmeticUnaryOperator
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ArrayLiteral
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Expression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlinedOxsts
import hu.bme.mit.semantifyr.oxsts.model.oxsts.LiteralBoolean
import hu.bme.mit.semantifyr.oxsts.model.oxsts.LiteralInteger
import hu.bme.mit.semantifyr.oxsts.model.oxsts.UnaryOp
import hu.bme.mit.semantifyr.xsts.lang.xsts.ArithmeticOp
import hu.bme.mit.semantifyr.xsts.lang.xsts.BooleanOp
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.writeText
import hu.bme.mit.semantifyr.xsts.lang.xsts.Expression as XstsExpression
import hu.bme.mit.semantifyr.xsts.lang.xsts.LiteralInteger as XstsLiteralInteger

@InjectWithOxsts
class WitnessValueTransformationTest {

    @Inject
    private lateinit var models: BackendTestModels

    @Inject
    private lateinit var injector: Injector

    private val model: InlinedOxsts by lazy {
        models.parse(
            """
                inlined oxsts of semantifyr::Anything
                var number : int := 0
                var flag : bool := false
                var slots : int[3] := [0, 0, 0]
                init { }
                tran { }
                prop { AG true }
            """,
        )
    }

    private suspend fun transform(expression: XstsExpression, variableName: String = "number"): Expression {
        val variable = model.variables.single {
            it.name == variableName
        }
        return withVerificationScope {
            injector.createChildInjector(ThetaBackendModule())
                .getInstance(XstsExpressionToOxstsExpressionTransformer::class.java)
                .transform(expression, variable)
        }
    }

    private fun integer(value: Int): XstsExpression {
        return XstsFactory.createLiteralInteger().also {
            it.value = value
        }
    }

    private fun boolean(value: Boolean): XstsExpression {
        return XstsFactory.createLiteralBoolean().also {
            it.isValue = value
        }
    }

    private fun arithmetic(
        left: Int,
        operator: ArithmeticOp,
        right: Int,
    ): XstsExpression {
        return XstsFactory.createArithmeticBinaryOperator().also {
            it.left = integer(left)
            it.op = operator
            it.right = integer(right)
        }
    }

    private fun logic(
        left: Boolean,
        operator: BooleanOp,
        right: Boolean,
    ): XstsExpression {
        return XstsFactory.createBooleanOperator().also {
            it.left = boolean(left)
            it.op = operator
            it.right = boolean(right)
        }
    }

    private fun array(default: Int, vararg elements: Pair<Int, Int>): XstsExpression {
        if (elements.isEmpty()) {
            return XstsFactory.createDefaultLiteralArray().also {
                it.indexType = XstsFactory.createIntegerType()
                it.elseExpression = integer(default)
            }
        }
        return XstsFactory.createConcreteLiteralArray().also { literal ->
            for ((index, value) in elements) {
                literal.values += XstsFactory.createLiteralArrayElement().also {
                    it.index = integer(index)
                    it.value = integer(value)
                }
            }
            literal.elseExpression = integer(default)
        }
    }

    private fun indexing(
        primary: XstsExpression,
        index: Int,
        written: Int? = null,
    ): XstsExpression {
        return XstsFactory.createWriteIndexingSuffixExpression().also { indexing ->
            indexing.primary = primary
            indexing.index = integer(index)
            written?.let {
                indexing.expression = integer(it)
            }
        }
    }

    private fun integerValue(expression: Expression): Int {
        return when (expression) {
            is LiteralInteger -> expression.value
            is ArithmeticUnaryOperator -> {
                assertThat(expression.op).isEqualTo(UnaryOp.MINUS)
                -integerValue(expression.body)
            }
            else -> error("Not an integer literal: $expression")
        }
    }

    private fun booleanValue(expression: Expression): Boolean {
        return (expression as LiteralBoolean).isValue
    }

    @Test
    suspend fun `iff is equality of booleans`() {
        assertThat(booleanValue(transform(logic(true, BooleanOp.IFF, false), "flag"))).isFalse
        assertThat(booleanValue(transform(logic(false, BooleanOp.IFF, false), "flag"))).isTrue
    }

    @Test
    suspend fun `imply holds unless its premise holds and its conclusion does not`() {
        assertThat(booleanValue(transform(logic(false, BooleanOp.IMPLY, false), "flag"))).isTrue
        assertThat(booleanValue(transform(logic(true, BooleanOp.IMPLY, false), "flag"))).isFalse
    }

    @Test
    suspend fun `modulo is never negative`() {
        assertThat(integerValue(transform(arithmetic(-5, ArithmeticOp.MOD, 3)))).isEqualTo(1)
        assertThat(integerValue(transform(arithmetic(5, ArithmeticOp.MOD, -3)))).isEqualTo(2)
        assertThat(integerValue(transform(arithmetic(-5, ArithmeticOp.MOD, -3)))).isEqualTo(1)
    }

    @Test
    suspend fun `rem takes the sign of the divisor`() {
        assertThat(integerValue(transform(arithmetic(5, ArithmeticOp.REM, 3)))).isEqualTo(2)
        assertThat(integerValue(transform(arithmetic(5, ArithmeticOp.REM, -3)))).isEqualTo(-2)
        assertThat(integerValue(transform(arithmetic(-5, ArithmeticOp.REM, 3)))).isEqualTo(1)
        assertThat(integerValue(transform(arithmetic(-5, ArithmeticOp.REM, -3)))).isEqualTo(-1)
    }

    @Test
    suspend fun `division is Euclidean`() {
        assertThat(integerValue(transform(arithmetic(-5, ArithmeticOp.DIV, 3)))).isEqualTo(-2)
        assertThat(integerValue(transform(arithmetic(-5, ArithmeticOp.DIV, -3)))).isEqualTo(2)
        assertThat(integerValue(transform(arithmetic(5, ArithmeticOp.DIV, -3)))).isEqualTo(-1)
    }

    @Test
    suspend fun `if-then-else takes the branch its guard selects`() {
        val expression = XstsFactory.createIfThenElseExpression().also {
            it.guard = logic(true, BooleanOp.AND, false)
            it.then = integer(1)
            it.`else` = integer(2)
        }

        assertThat(integerValue(transform(expression))).isEqualTo(2)
    }

    @Test
    suspend fun `write-indexing replaces one slot and reading takes the default where nothing was written`() {
        val written = indexing(array(0, 0 to 1), 1, written = 5)

        val literal = transform(written, "slots") as ArrayLiteral

        assertThat(
            literal.values.map {
                integerValue(it)
            },
        ).containsExactly(1, 5, 0)
        assertThat(integerValue(transform(indexing(written, 2)))).isEqualTo(0)
        assertThat(integerValue(transform(indexing(written, 1)))).isEqualTo(5)
    }

    @Test
    suspend fun `a default-literal array fills every slot of the variable`() {
        val literal = transform(array(4), "slots") as ArrayLiteral

        assertThat(
            literal.values.map {
                integerValue(it)
            },
        ).containsExactly(4, 4, 4)
    }

    @Test
    suspend fun `a value beyond 32 bits is reported, not wrapped`() {
        val failure = runCatching {
            transform(arithmetic(Int.MAX_VALUE, ArithmeticOp.ADD, 1))
        }.exceptionOrNull()

        assertThat(failure).isInstanceOf(UnrepresentableWitnessValueException::class.java).hasMessageContaining("2147483648")
    }

    @Test
    fun `counterexample values are read whole`(@TempDir directory: Path) {
        val cexFile = directory.resolve("out.cex")
        cexFile.writeText("(XstsStateSequence (XstsState pre_init last_env (ExplState (number -2147483648) (y 3000000000))))")

        val values = CexWitnessTransformer().transform(CexReader().loadCexModel(cexFile)).initialState.variableValues
        val transformer = CexExpressionToXstsExpressionTransformer(XstsFactory.createXstsModel())

        val least = transformer.transform(values[0].value) as XstsLiteralInteger
        assertThat(least.value).isEqualTo(Int.MIN_VALUE)
        assertThatThrownBy {
            transformer.transform(values[1].value)
        }.isInstanceOf(UnrepresentableWitnessValueException::class.java)
            .hasMessageContaining("3000000000")
    }
}
