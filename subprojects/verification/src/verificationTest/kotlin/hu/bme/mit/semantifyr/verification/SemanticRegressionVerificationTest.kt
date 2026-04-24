/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.verification

import hu.bme.mit.semantifyr.backend.VerificationCase
import hu.bme.mit.semantifyr.oxsts.lang.OxstsStandaloneSetup
import hu.bme.mit.semantifyr.oxsts.lang.tests.InjectWithOxsts
import hu.bme.mit.semantifyr.portfolios.Portfolios
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import kotlin.io.path.Path

@InjectWithOxsts
class LanguageFeatureVerificationTests {

    companion object {

        private val injector = OxstsStandaloneSetup().createInjectorAndDoEMFRegistration()
        private val helper = injector.getInstance(SemantifyrVerifierTestHelper::class.java)

        private fun loadModel(fileName: String) =
            helper.semantifyrLoader.startContext()
                .loadModel(Path("test-models/language-features/$fileName"))
                .buildAndResolve()

        private val primitivesModel by lazy { loadModel("primitives.oxsts") }
        private val controlFlowModel by lazy { loadModel("control_flow.oxsts") }
        private val classesModel by lazy { loadModel("classes_and_inheritance.oxsts") }
        private val inlineCallsModel by lazy { loadModel("inline_calls.oxsts") }
        private val propertyCallsModel by lazy { loadModel("property_calls.oxsts") }
        private val variableDispatchModel by lazy { loadModel("variable_dispatch.oxsts") }
        private val enumsModel by lazy { loadModel("enums.oxsts") }
        private val parametricModel by lazy { loadModel("parametric.oxsts") }
        private val temporalModel by lazy { loadModel("temporal.oxsts") }
        private val expressionsModel by lazy { loadModel("expressions.oxsts") }
        private val localVarsModel by lazy { loadModel("local_vars.oxsts") }
        private val advancedFeaturesModel by lazy { loadModel("advanced_features.oxsts") }
        private val navigationModel by lazy { loadModel("navigation.oxsts") }

        @JvmStatic
        fun `Primitives`(): List<Arguments> = helper.collectVerificationCasesAsArguments(primitivesModel)

        @JvmStatic
        fun `Control flow`(): List<Arguments> = helper.collectVerificationCasesAsArguments(controlFlowModel)

        @JvmStatic
        fun `Classes and inheritance`(): List<Arguments> = helper.collectVerificationCasesAsArguments(classesModel)

        @JvmStatic
        fun `Inline calls`(): List<Arguments> = helper.collectVerificationCasesAsArguments(inlineCallsModel)

        @JvmStatic
        fun `Property calls`(): List<Arguments> = helper.collectVerificationCasesAsArguments(propertyCallsModel)

        @JvmStatic
        fun `Variable dispatch`(): List<Arguments> = helper.collectVerificationCasesAsArguments(variableDispatchModel)

        @JvmStatic
        fun `Enums`(): List<Arguments> = helper.collectVerificationCasesAsArguments(enumsModel)

        @JvmStatic
        fun `Parametric calls`(): List<Arguments> = helper.collectVerificationCasesAsArguments(parametricModel)

        @JvmStatic
        fun `Temporal operators`(): List<Arguments> = helper.collectVerificationCasesAsArguments(temporalModel)

        @JvmStatic
        fun `Expressions`(): List<Arguments> = helper.collectVerificationCasesAsArguments(expressionsModel)

        @JvmStatic
        fun `Local variables`(): List<Arguments> = helper.collectVerificationCasesAsArguments(localVarsModel)

        @JvmStatic
        fun `Advanced features`(): List<Arguments> = helper.collectVerificationCasesAsArguments(advancedFeaturesModel)

        @JvmStatic
        fun `Navigation edge cases`(): List<Arguments> = helper.collectVerificationCasesAsArguments(navigationModel)
    }

    @ParameterizedTest
    @MethodSource
    suspend fun `Primitives`(verificationCase: VerificationCase) {
        helper.checkVerificationCase(primitivesModel, verificationCase, Portfolios.ThetaFull)
    }

}
