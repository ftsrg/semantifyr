/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backend.execution

import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

data class RunexecRun(
    val returnValue: Int?,
    val exitSignal: Int?,
    val terminationReason: String?,
    val wallTime: Duration?,
    val cpuTime: Duration?,
    val memory: Long?,
) {
    val exitCode: Int?
        get() = returnValue ?: exitSignal?.let { SIGNAL_EXIT_OFFSET + it }

    val isTimeout: Boolean
        get() = terminationReason == "cputime" || terminationReason == "walltime"

    companion object {
        private const val SIGNAL_EXIT_OFFSET = 128

        private val resultLine = Regex("""^([a-z][a-z0-9-]*)=(.*)$""")

        fun parse(output: String): RunexecRun {
            val values = output.lines().mapNotNull {
                resultLine.matchEntire(it.trim())?.destructured
            }.associate { (key, value) ->
                key to value
            }
            return RunexecRun(
                returnValue = values["returnvalue"]?.toIntOrNull(),
                exitSignal = values["exitsignal"]?.toIntOrNull(),
                terminationReason = values["terminationreason"],
                wallTime = values["walltime"]?.removeSuffix("s")?.toDoubleOrNull()?.seconds,
                cpuTime = values["cputime"]?.removeSuffix("s")?.toDoubleOrNull()?.seconds,
                memory = values["memory"]?.removeSuffix("B")?.toLongOrNull(),
            )
        }
    }
}

// a coroutine context element, so every backend process started below it runs under BenchExec's runexec
class Runexec(
    val options: List<String> = emptyList(),
    private val executable: String = DEFAULT_EXECUTABLE,
) : AbstractCoroutineContextElement(Runexec) {
    private val recorded = ConcurrentLinkedQueue<RunexecRun>()

    val runs: List<RunexecRun>
        get() = recorded.toList()

    // runexec merges stderr into its output file behind a header, so the tool keeps writing its own files
    fun wrap(
        command: List<String>,
        workingDirectory: File,
        logFile: File?,
        errorFile: File?,
    ): List<String> {
        val runexecOutput = workingDirectory.resolve("runexec.out")
        return listOf(executable) + options + listOf(
            "--dir",
            workingDirectory.absolutePath,
            "--output",
            runexecOutput.absolutePath,
            "--",
            "sh",
            "-c",
            REDIRECT,
            "sh",
            logFile?.absolutePath ?: NULL_DEVICE,
            errorFile?.absolutePath ?: NULL_DEVICE,
        ) + command
    }

    fun record(run: RunexecRun) {
        recorded += run
    }

    // an installed runexec may still lack the cgroups it needs, so the probe runs a command under it
    fun isUsable(): Boolean {
        val output = File.createTempFile("runexec-probe-", ".txt")
        try {
            val process = ProcessBuilder(listOf(executable) + options + listOf("--output", NULL_DEVICE, "--", "true"))
                .redirectOutput(output)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start()
            if (!process.waitFor(PROBE_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                return false
            }
            return RunexecRun.parse(output.readText()).returnValue == 0
        } catch (_: IOException) {
            return false
        } finally {
            output.delete()
        }
    }

    companion object Key : CoroutineContext.Key<Runexec> {
        const val DEFAULT_EXECUTABLE = "runexec"

        private const val NULL_DEVICE = "/dev/null"
        private const val REDIRECT = "log=\"$1\"; err=\"$2\"; shift 2; exec \"$@\" >>\"\$log\" 2>>\"\$err\""

        private const val PROBE_SECONDS = 10L
    }
}
