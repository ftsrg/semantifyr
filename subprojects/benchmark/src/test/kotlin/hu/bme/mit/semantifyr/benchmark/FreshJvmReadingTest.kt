/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.benchmark

import kotlinx.serialization.builtins.ListSerializer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.time.Duration.Companion.seconds

class FreshJvmReadingTest {

    @TempDir
    lateinit var directory: Path

    private val jvm = ProcessObservation.parse(checkNotNull(javaClass.getResource("/runexec/finished.txt")).readText())

    private val hostState = HostState(LoadAverage(1.5, 2.0, 2.5), LoadAverage(1.6, 2.0, 2.5))

    private val warmUpCalls = listOf(
        Call<BackendAnswer>(true, listOf(SpanObservation("flattening", 1200)), emptyList(), null),
        Call<BackendAnswer>(true, listOf(SpanObservation("flattening", 900)), emptyList(), null),
    )

    private fun writeCalls(calls: List<Call<BackendAnswer>>) {
        writeAtomically(directory.resolve("calls.json"), benchmarkJson.encodeToString(ListSerializer(Call.serializer(BackendAnswer.serializer())), calls))
    }

    private fun read(): Measured<BackendAnswer> {
        return readFreshJvm(directory, BackendAnswer.serializer(), hostState, 60.seconds, jvm)
    }

    @Test
    fun `a finished run is read as its warm-up calls and its measured call with the result`() {
        val measuredCall = Call(false, listOf(SpanObservation("flattening", 800)), emptyList(), BackendAnswer("Passed", null))
        writeCalls(warmUpCalls + measuredCall)

        val measured = read()

        assertThat(measured.calls).containsExactlyElementsOf(warmUpCalls + measuredCall)
        assertThat(measured.failure).isNull()
        assertThat(measured.jvm).isEqualTo(jvm)
    }

    @Test
    fun `a run cut before its measured call is read as the calls it wrote and no result`() {
        writeCalls(warmUpCalls)

        val measured = read()

        assertThat(measured.calls).containsExactlyElementsOf(warmUpCalls)
        assertThat(measured.failure).isNull()
    }

    @Test
    fun `a failed run is read as its failure beside the calls it wrote before`() {
        val failure = Failure(Stage.Call, "java.lang.IllegalStateException: refused")
        writeCalls(warmUpCalls)
        writeAtomically(directory.resolve("failure.json"), benchmarkJson.encodeToString(Failure.serializer(), failure))

        val measured = read()

        assertThat(measured.failure).isEqualTo(failure)
        assertThat(measured.calls).containsExactlyElementsOf(warmUpCalls)
    }
}
