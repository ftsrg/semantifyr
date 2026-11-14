/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import com.google.inject.Inject
import com.google.inject.Provider
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.GammaPackage
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.GammaRoot
import hu.bme.mit.semantifyr.frontends.gamma.lang.tests.InjectWithGamma
import hu.bme.mit.semantifyr.frontends.gamma.lang.validation.GammaValidator
import org.assertj.core.api.Assertions.assertThat
import org.eclipse.emf.common.util.URI
import org.eclipse.xtext.resource.SaveOptions
import org.eclipse.xtext.resource.XtextResourceSet
import org.eclipse.xtext.serializer.ISerializer
import org.eclipse.xtext.testing.util.ParseHelper
import org.eclipse.xtext.testing.validation.ValidationTestHelper
import org.junit.jupiter.api.Test
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString

@InjectWithGamma
class GammaTraceLanguageTest {

    @Inject
    private lateinit var parseHelper: ParseHelper<GammaRoot>

    @Inject
    private lateinit var validationTestHelper: ValidationTestHelper

    @Inject
    private lateinit var resourceSetProvider: Provider<XtextResourceSet>

    @Inject
    private lateinit var serializer: ISerializer

    @Test
    fun `a trace of a case links every assertion to the traced component`() {
        val trace = parseTrace("Crossroads", "RoadBCanGetGreen", "state trafficLightA.NormalRegion.Green")

        validationTestHelper.assertNoErrors(trace)
    }

    @Test
    fun `a raised event assertion names an output event`() {
        val trace = parseTrace("Crossroads", "RoadBCanGetGreen", "raise control.police")

        validationTestHelper.assertError(trace, GammaPackage.Literals.RAISE_EVENT_ACT, GammaValidator.INVALID_TRACE_EVENT)
    }

    @Test
    fun `a fired transition must exist`() {
        val trace = parseTrace("Crossroads", "RoadBCanGetGreen", "transition controller from BothYellow to TrafficOnA")

        validationTestHelper.assertError(trace, GammaPackage.Literals.FIRED_TRANSITION_EXPRESSION, GammaValidator.UNKNOWN_TRACE_TRANSITION)
    }

    @Test
    fun `transitions between the same states are told apart by their trigger`() {
        val ambiguous = parseTrace("Spacecraft", "TransmissionCanStart", "transition station from Idle to Operation")
        val disambiguated = parseTrace("Spacecraft", "TransmissionCanStart", "transition station from Idle to Operation when timeout startTimeout")

        validationTestHelper.assertError(ambiguous, GammaPackage.Literals.FIRED_TRANSITION_EXPRESSION, GammaValidator.AMBIGUOUS_TRACE_TRANSITION)
        validationTestHelper.assertNoErrors(disambiguated)
    }

    @Test
    fun `a variable value matches the variable type`() {
        val trace = parseTrace("Spacecraft", "TransmissionCanStart", "variable spacecraft.recharging = 5")

        validationTestHelper.assertError(trace, GammaPackage.Literals.COMPARISON_OPERATOR, GammaValidator.INVALID_TRACE_VALUE)
    }

    @Test
    fun `a negative variable value round-trips through the formatter`() {
        val trace = parseTrace("Spacecraft", "TransmissionCanStart", "variable spacecraft.batteryCharge = - 10")

        validationTestHelper.assertNoErrors(trace)
        assertThat(serializer.serialize(trace, SaveOptions.newBuilder().format().options)).contains("variable spacecraft.batteryCharge = -10;")
    }

    private fun parseTrace(
        model: String,
        verificationCase: String,
        assertion: String,
    ): GammaRoot {
        val resourceSet = resourceSetProvider.get()
        resourceSet.getResource(URI.createFileURI(Path("build", "test-models", "$model.gamma").absolutePathString()), true)
        return parseHelper.parse(
            """
            import $model

            trace Checked of $verificationCase

            step {
                assert {
                    $assertion;
                }
            }
            """.trimIndent(),
            URI.createURI("Checked.get"),
            resourceSet,
        )
    }
}
