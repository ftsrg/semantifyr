/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.frontends.gamma.lang

import com.google.inject.Inject
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.GammaPackage
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.GammaRoot
import hu.bme.mit.semantifyr.frontends.gamma.lang.tests.InjectWithGamma
import hu.bme.mit.semantifyr.frontends.gamma.lang.validation.GammaValidator
import org.eclipse.emf.common.util.URI
import org.eclipse.xtext.resource.XtextResourceSet
import org.eclipse.xtext.testing.util.ParseHelper
import org.eclipse.xtext.testing.validation.ValidationTestHelper
import org.junit.jupiter.api.Test

@InjectWithGamma
class GammaValidatorTest {

    @Inject
    private lateinit var parseHelper: ParseHelper<GammaRoot>

    @Inject
    private lateinit var validationTestHelper: ValidationTestHelper

    private fun statechart(body: String, annotation: String = ""): GammaRoot {
        return parseHelper.parse(
            """
                package test
                interface Control {
                    in event start
                }
                $annotation
                statechart Machine [
                    port control : provides Control
                ] {
                    timeout delay
                    region Main {
                        initial Start
                        state Idle
                        state Busy
                    }
                    transition from Start to Idle
                    $body
                }
            """.trimIndent(),
        )
    }

    @Test
    fun `a transition leaving a state needs a trigger`() {
        val model = statechart("transition from Idle to Busy")

        validationTestHelper.assertError(model, GammaPackage.Literals.TRANSITION, GammaValidator.TRIGGERLESS_TRANSITION)
    }

    @Test
    fun `a timeout is set to one value`() {
        val model = statechart(
            """
                transition from Idle to Busy when control.start / set delay := 1 s;
                transition from Busy to Idle when control.start / set delay := 2 s;
            """,
        )

        validationTestHelper.assertError(model, GammaPackage.Literals.SET_TIMEOUT_ACTION, GammaValidator.TIMEOUT_SET_TO_DIFFERENT_VALUES)
    }

    @Test
    fun `a time value is positive`() {
        val model = statechart("transition from Idle to Busy when control.start / set delay := 0 s;")

        validationTestHelper.assertError(model, GammaPackage.Literals.SET_TIMEOUT_ACTION, GammaValidator.NON_POSITIVE_TIME)
    }

    @Test
    fun `state names are unique within a statechart`() {
        val model = parseHelper.parse(
            """
                package test
                statechart Machine {
                    region Main {
                        initial Start
                        state Idle {
                            region Inner {
                                initial InnerStart
                                state Idle
                            }
                        }
                    }
                    transition from Start to Idle
                }
            """.trimIndent(),
        )

        validationTestHelper.assertError(model, GammaPackage.Literals.STATE, GammaValidator.DUPLICATE_STATE_NAME)
    }

    @Test
    fun `transitions sharing source, target and trigger ask for an id`() {
        val unnamed = statechart(
            """
                transition from Idle to Busy when control.start
                transition from Idle to Busy when control.start
            """,
        )
        val named = statechart(
            """
                @(First)
                transition from Idle to Busy when control.start
                @(Second)
                transition from Idle to Busy when control.start
            """,
        )

        validationTestHelper.assertWarning(unnamed, GammaPackage.Literals.TRANSITION, GammaValidator.INDISTINGUISHABLE_TRANSITIONS)
        validationTestHelper.assertNoIssue(named, GammaPackage.Literals.TRANSITION, GammaValidator.INDISTINGUISHABLE_TRANSITIONS)
    }

    @Test
    fun `a priority has no effect under priority off`() {
        val off = statechart("transition (2) from Idle to Busy when control.start")
        val valueBased = statechart("transition (2) from Idle to Busy when control.start", annotation = "@TransitionPriority = value-based")

        validationTestHelper.assertWarning(off, GammaPackage.Literals.TRANSITION, GammaValidator.INEFFECTIVE_PRIORITY)
        validationTestHelper.assertNoIssue(valueBased, GammaPackage.Literals.TRANSITION, GammaValidator.INEFFECTIVE_PRIORITY)
    }

    @Test
    fun `a trace file holds a trace`() {
        val model = parseHelper.parse("package test", URI.createURI("test.get"), XtextResourceSet())

        validationTestHelper.assertError(model, GammaPackage.Literals.GAMMA_MODEL_PACKAGE, GammaValidator.INVALID_FILE_CONTENT)
    }
}
