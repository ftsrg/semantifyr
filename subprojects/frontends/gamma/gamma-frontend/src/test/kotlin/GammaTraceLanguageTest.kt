/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import com.google.inject.Inject
import com.google.inject.Provider
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.GammaModelPackage
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.GammaPackage
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
    private lateinit var parseHelper: ParseHelper<GammaModelPackage>

    @Inject
    private lateinit var validationTestHelper: ValidationTestHelper

    @Inject
    private lateinit var resourceSetProvider: Provider<XtextResourceSet>

    @Inject
    private lateinit var serializer: ISerializer

    @Test
    fun `a state assertion must name a state`() {
        val trace = parseTrace("Crossroads", "ACanBeGreen", "state system.controller.controlA")

        validationTestHelper.assertError(trace, GammaPackage.Literals.ACTIVE_STATE_ASSERTION, GammaValidator.INVALID_TRACE_PATH)
    }

    @Test
    fun `a trace path starts at the component instance of the traced case`() {
        val trace = parseTrace("Crossroads", "ACanBeGreen", "state CentralController.Main.Operating")

        validationTestHelper.assertError(trace, GammaPackage.Literals.ELEMENT_REFERENCE_EXPRESSION, GammaValidator.INVALID_TRACE_PATH)
    }

    @Test
    fun `a raised event assertion names an output event`() {
        val trace = parseTrace("Crossroads", "ACanBeGreen", "raise system.controller.control.police")

        validationTestHelper.assertError(trace, GammaPackage.Literals.RAISED_EVENT_ASSERTION, GammaValidator.INVALID_TRACE_EVENT)
    }

    @Test
    fun `a fired transition must exist`() {
        val trace = parseTrace("Crossroads", "ACanBeGreen", "transition system.controller.Main.Operating.OperatingRegion.BothYellow to TrafficOnA")

        validationTestHelper.assertError(trace, GammaPackage.Literals.FIRED_TRANSITION_ASSERTION, GammaValidator.UNKNOWN_TRACE_TRANSITION)
    }

    @Test
    fun `transitions between the same states are told apart by their trigger`() {
        val ambiguous = parseTrace("Spacecraft", "Station_OperationReachable", "transition mission.station.Main.Idle to Operation")
        val disambiguated = parseTrace("Spacecraft", "Station_OperationReachable", "transition mission.station.Main.Idle to Operation when timeout startTimeout")

        validationTestHelper.assertError(ambiguous, GammaPackage.Literals.FIRED_TRANSITION_ASSERTION, GammaValidator.AMBIGUOUS_TRACE_TRANSITION)
        validationTestHelper.assertNoErrors(disambiguated)
    }

    @Test
    fun `a variable value matches the variable type`() {
        val trace = parseTrace("Spacecraft", "Spacecraft_BatteryRechargingReachable", "variable mission.spacecraft.recharging == 5")

        validationTestHelper.assertError(trace, GammaPackage.Literals.VARIABLE_VALUE_ASSERTION, GammaValidator.INVALID_TRACE_VALUE)
    }

    @Test
    fun `a negative variable value round-trips through the formatter`() {
        val trace = parseTrace("Spacecraft", "Spacecraft_BatteryRechargingReachable", "variable mission.spacecraft.batteryCharge == - 10")

        validationTestHelper.assertNoErrors(trace)
        assertThat(serializer.serialize(trace, SaveOptions.newBuilder().format().options)).contains("variable mission.spacecraft.batteryCharge == -10")
    }

    private fun parseTrace(
        model: String,
        verificationCase: String,
        assertion: String,
    ): GammaModelPackage {
        val resourceSet = resourceSetProvider.get()
        resourceSet.getResource(URI.createFileURI(Path("build", "test-models", "$model.gamma").absolutePathString()), true)
        return parseHelper.parse(
            """
            package $model

            trace Checked of $verificationCase {
                step {
                    assert {
                        $assertion
                    }
                }
            }
            """.trimIndent(),
            resourceSet,
        )
    }
}
