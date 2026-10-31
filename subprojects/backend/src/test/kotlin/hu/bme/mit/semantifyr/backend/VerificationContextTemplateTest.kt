/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backend

import hu.bme.mit.semantifyr.backend.execution.BackendExecutor
import hu.bme.mit.semantifyr.backend.witness.InlinedWitness
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlinedOxsts
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.mock
import kotlin.coroutines.cancellation.CancellationException
import kotlin.io.path.Path
import kotlin.time.Duration

class VerificationContextTemplateTest {

    private class CountingExecutor(
        private val failPreparation: Boolean = false,
    ) : BackendExecutor {
        var preparations = 0

        override fun isAvailable(): Boolean {
            return true
        }

        override fun prepare() {
            preparations++
            check(!failPreparation) {
                "image pull failed"
            }
        }
    }

    private class ScriptedContext(
        override val executor: CountingExecutor = CountingExecutor(),
        private val prepareStep: () -> String = { "artifacts" },
        private val runStep: () -> String? = { "pass" },
        private val witness: InlinedWitness? = null,
    ) : VerificationContext<String, String>(
        backendId = "scripted",
        request = BackendVerificationRequest(mock<InlinedOxsts>(), Path("build")),
    ) {
        val phases = mutableListOf<String>()

        override suspend fun prepare(): String {
            phases += "prepare"
            return prepareStep()
        }

        override suspend fun run(artifacts: String): String? {
            phases += "run $artifacts"
            return runStep()
        }

        override fun interpret(rawVerdict: String?, artifacts: String): VerificationVerdict {
            phases += "interpret $rawVerdict"
            return when (rawVerdict) {
                "pass" -> VerificationVerdict.Passed
                "fail" -> VerificationVerdict.Failed
                else -> VerificationVerdict.Inconclusive
            }
        }

        override suspend fun buildWitness(rawVerdict: String?, artifacts: String): InlinedWitness? {
            phases += "witness $rawVerdict"
            return witness
        }
    }

    @Test
    suspend fun `the phases run in order on a prepared executor`() {
        val executor = CountingExecutor()
        val context = ScriptedContext(executor)

        val result = context.execute()

        assertThat(context.phases).containsExactly("prepare", "run artifacts", "interpret pass", "witness pass")
        assertThat(executor.preparations).isEqualTo(1)
        assertThat(result.verdict).isEqualTo(VerificationVerdict.Passed)
        assertThat(result.metadata.backendId).isEqualTo("scripted")
        assertThat(result.message).isNull()
    }

    @Test
    suspend fun `a run without a raw verdict is interpreted by the backend`() {
        val result = ScriptedContext(runStep = { null }).execute()

        assertThat(result.verdict).isEqualTo(VerificationVerdict.Inconclusive)
    }

    @Test
    suspend fun `the witness is passed through and its back-annotation is timed`() {
        val witness = mock<InlinedWitness>()

        val result = ScriptedContext(runStep = { "fail" }, witness = witness).execute()

        assertThat(result.verdict).isEqualTo(VerificationVerdict.Failed)
        assertThat(result.inlinedWitness).isSameAs(witness)
        assertThat(result.metrics.backAnnotationDuration).isGreaterThan(Duration.ZERO)
    }

    @Test
    suspend fun `no witness means no back-annotation time`() {
        val result = ScriptedContext().execute()

        assertThat(result.inlinedWitness).isNull()
        assertThat(result.metrics.backAnnotationDuration).isEqualTo(Duration.ZERO)
    }

    @Test
    suspend fun `an unsupported construct during preparation is NotSupported with its message`() {
        val context = ScriptedContext(prepareStep = { throw BackendUnsupportedException("Spin does not support for-operations") })

        val result = context.execute()

        assertThat(result.verdict).isEqualTo(VerificationVerdict.NotSupported)
        assertThat(result.message).isEqualTo("Spin does not support for-operations")
        assertThat(context.phases).containsExactly("prepare")
    }

    @Test
    suspend fun `an unsupported construct found by the run is NotSupported`() {
        val result = ScriptedContext(runStep = { throw BackendUnsupportedException("value out of range") }).execute()

        assertThat(result.verdict).isEqualTo(VerificationVerdict.NotSupported)
        assertThat(result.message).isEqualTo("value out of range")
    }

    @Test
    suspend fun `any other failure is Errored with its message`() {
        val result = ScriptedContext(runStep = { error("spin execution failed with exit code 2") }).execute()

        assertThat(result.verdict).isEqualTo(VerificationVerdict.Errored)
        assertThat(result.message).isEqualTo("spin execution failed with exit code 2")
    }

    @Test
    suspend fun `a failing executor preparation is Errored before any phase`() {
        val context = ScriptedContext(executor = CountingExecutor(failPreparation = true))

        val result = context.execute()

        assertThat(result.verdict).isEqualTo(VerificationVerdict.Errored)
        assertThat(result.message).isEqualTo("image pull failed")
        assertThat(context.phases).isEmpty()
    }

    @Test
    suspend fun `cancellation propagates`() {
        val context = ScriptedContext(runStep = { throw CancellationException("cancelled") })

        assertThrows<CancellationException> {
            context.execute()
        }
    }
}
