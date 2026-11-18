/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.benchmark

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.writeText
import kotlin.time.Duration.Companion.seconds

class FreshJvmReadingTest {

    @TempDir
    lateinit var directory: Path

    private val jvm = ProcessObservation.parse(checkNotNull(javaClass.getResource("/runexec/finished.txt")).readText())

    private val hostState = HostState(LoadAverage(1.5, 2.0, 2.5), LoadAverage(1.6, 2.0, 2.5))

    private val warmUpCallsOnly = """
        [
            {
                "warmUp": true,
                "spans": [
                    {
                        "name": "flattening",
                        "wallTimeNanoseconds": 1200
                    }
                ],
                "processes": [],
                "result": null
            },
            {
                "warmUp": true,
                "spans": [
                    {
                        "name": "flattening",
                        "wallTimeNanoseconds": 900
                    }
                ],
                "processes": [],
                "result": null
            }
        ]
    """.trimIndent()

    private fun read(): Measured<BackendAnswer> {
        return readFreshJvm(directory, BackendAnswer.serializer(), hostState, 60.seconds, jvm)
    }

    @Test
    fun `a finished run is read as its warm-up calls and its measured call with the result`() {
        directory.resolve("calls.json").writeText(
            """
                [
                    {
                        "warmUp": true,
                        "spans": [
                            {
                                "name": "flattening",
                                "wallTimeNanoseconds": 1200
                            }
                        ],
                        "processes": [],
                        "result": null
                    },
                    {
                        "warmUp": false,
                        "spans": [
                            {
                                "name": "flattening",
                                "wallTimeNanoseconds": 800
                            }
                        ],
                        "processes": [],
                        "result": {
                            "verdict": "Passed",
                            "message": null
                        }
                    }
                ]
            """.trimIndent(),
        )

        val measured = read()

        val warmUps = measured.calls.map {
            it.warmUp
        }
        assertThat(warmUps).containsExactly(true, false)
        assertThat(measured.calls.last().result).isEqualTo(BackendAnswer("Passed", null))
        assertThat(measured.calls.last().spans).containsExactly(SpanObservation("flattening", 800))
        assertThat(measured.failure).isNull()
        assertThat(measured.jvm).isEqualTo(jvm)
    }

    @Test
    fun `a run cut before its measured call is read as the calls it wrote and no result`() {
        directory.resolve("calls.json").writeText(warmUpCallsOnly)

        val measured = read()

        assertThat(measured.calls).hasSize(2).allSatisfy {
            assertThat(it.warmUp).isTrue()
            assertThat(it.result).isNull()
        }
        assertThat(measured.failure).isNull()
    }

    @Test
    fun `a failed run is read as its failure beside the calls it wrote before`() {
        directory.resolve("calls.json").writeText(warmUpCallsOnly)
        directory.resolve("failure.json").writeText(
            """
                {
                    "stage": "Call",
                    "stackTrace": "java.lang.IllegalStateException: refused"
                }
            """.trimIndent(),
        )

        val measured = read()

        assertThat(measured.failure).isEqualTo(Failure(Stage.Call, "java.lang.IllegalStateException: refused"))
        assertThat(measured.calls).hasSize(2)
    }
}
