/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backend

import com.google.inject.Guice
import com.google.inject.Injector
import hu.bme.mit.semantifyr.backend.execution.BackendExecutor
import hu.bme.mit.semantifyr.backend.execution.ExecutionEnvironment
import hu.bme.mit.semantifyr.backend.execution.ExecutorKey
import hu.bme.mit.semantifyr.compiler.pipeline.utils.OxstsFactory
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlinedOxsts
import hu.bme.mit.semantifyr.oxsts.model.oxsts.VariableDeclaration
import org.assertj.core.api.Assertions.assertThat
import org.eclipse.emf.ecore.EObject
import org.junit.jupiter.api.Test

class VerificationBackendTest {

    private class ExactConfig : BackendConfig {
        override val id = "exact"
        override val soundness = SoundnessDirection.Exact
    }

    private class UnderApproximatingVariables : BackendCapabilityChecker() {
        override fun describeUnsupported(element: EObject): String? {
            return null
        }

        override fun soundness(inlinedOxsts: InlinedOxsts, configured: SoundnessDirection): SoundnessDirection {
            if (inlinedOxsts.variables.isEmpty()) {
                return configured
            }
            return SoundnessDirection.UnderApproximating
        }
    }

    private class RefusingVariables : BackendCapabilityChecker() {
        override fun describeUnsupported(element: EObject): String? {
            if (element is VariableDeclaration) {
                return "variables"
            }
            return null
        }
    }

    private class CheckedBackend(
        private val checker: BackendCapabilityChecker,
    ) : VerificationBackend<ExactConfig>() {
        override val id = "checked"
        override val executorKey: ExecutorKey<BackendExecutor>
            get() = error("never dispatched")

        override suspend fun verify(
            parentInjector: Injector,
            config: ExactConfig,
            request: BackendVerificationRequest,
            environment: ExecutionEnvironment,
        ): BackendVerificationResult {
            error("never dispatched")
        }

        override fun capabilityChecker(parentInjector: Injector, config: ExactConfig): BackendCapabilityChecker {
            return checker
        }
    }

    private val injector = Guice.createInjector()

    @Test
    fun `a backend trusts either verdict of a configuration that stays exact on the model`() {
        val backend = CheckedBackend(UnderApproximatingVariables())
        val inlinedOxsts = modelChecking(OxstsFactory.createAG())

        assertThat(backend.trusts(injector, ExactConfig(), inlinedOxsts, VerificationVerdict.Passed)).isTrue
        assertThat(backend.trusts(injector, ExactConfig(), inlinedOxsts, VerificationVerdict.Failed)).isTrue
    }

    @Test
    fun `a backend trusts only a verdict with a trace on a model its checker answers under-approximated`() {
        val backend = CheckedBackend(UnderApproximatingVariables())
        val inlinedOxsts = withVariable(modelChecking(OxstsFactory.createAG()))

        assertThat(backend.trusts(injector, ExactConfig(), inlinedOxsts, VerificationVerdict.Passed)).isFalse
        assertThat(backend.trusts(injector, ExactConfig(), inlinedOxsts, VerificationVerdict.Failed)).isTrue
    }

    @Test
    fun `a backend trusts no verdict on a model its checker refuses`() {
        val backend = CheckedBackend(RefusingVariables())
        val inlinedOxsts = withVariable(modelChecking(OxstsFactory.createAG()))

        assertThat(backend.trusts(injector, ExactConfig(), inlinedOxsts, VerificationVerdict.Failed)).isFalse
    }

    private fun withVariable(inlinedOxsts: InlinedOxsts): InlinedOxsts {
        inlinedOxsts.variables += OxstsFactory.createVariableDeclaration()
        return inlinedOxsts
    }
}
