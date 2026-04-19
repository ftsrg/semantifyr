/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backend.execution

import hu.bme.mit.semantifyr.logging.debug
import hu.bme.mit.semantifyr.logging.info
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import org.slf4j.Logger
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Shared plumbing for backends that talk to a model-checker binary via the system shell. Subclasses
 * name the binary and typically wrap [runProcess]/[probeAvailability] with their own typed
 * `execute(spec)` / `isAvailable()` shape. The Windows `cmd /c` prefix, cancellation-safe waitFor,
 * process-tree teardown, and output redirection are handled here so backend-specific executors can
 * stay small.
 */
abstract class BaseShellExecutor {

    protected abstract val logger: Logger

    /** The binary that is expected to be on PATH, e.g. `spin`, `verifyta`, `nuXmv`. */
    protected abstract val binaryName: String

    private val isWindows: Boolean = System.getProperty("os.name").lowercase().contains("windows")

    /**
     * Probes the binary by running it with [probeArgs] (typically `-V` or `-h`) under a short
     * [timeoutSeconds]. If [expectedExitCode] is non-null, the exit code must match; if null, any
     * successful wait-for counts as available (useful for tools that return non-zero on `--help`).
     */
    protected fun probeAvailability(
        probeArgs: List<String>,
        timeoutSeconds: Long = 2,
        expectedExitCode: Int? = 0,
    ): Boolean {
        val available = runCatching {
            val process = buildProcess(probeArgs)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start()
            if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                false
            } else {
                expectedExitCode?.let { process.exitValue() == it } ?: true
            }
        }.getOrElse { false }
        logger.debug { "$binaryName on PATH: $available" }
        return available
    }

    /**
     * Runs the binary with the given [args] in [workingDirectory] and returns its exit code.
     * Redirects stdout/stderr to [logFile]/[errorFile] if provided, emitting an optional [header]
     * into the log before the process starts so operators can tell which invocation is which.
     * Safe to cancel from a coroutine: on cancellation the process tree is destroyed and awaited.
     */
    protected suspend fun runProcess(
        args: List<String>,
        workingDirectory: File,
        logFile: File? = null,
        errorFile: File? = null,
        header: String? = null,
    ): Int = coroutineScope {
        logger.info { "Starting $binaryName in ${workingDirectory.absolutePath}" }
        logger.debug { "$binaryName command: ${args.joinToString(" ")}" }

        prepareOutputFiles(logFile, errorFile, header)

        val logRedirect = logFile?.let { ProcessBuilder.Redirect.appendTo(it) } ?: ProcessBuilder.Redirect.DISCARD
        val errorRedirect = errorFile?.let { ProcessBuilder.Redirect.appendTo(it) } ?: ProcessBuilder.Redirect.DISCARD

        val process = buildProcess(args)
            .directory(workingDirectory)
            .redirectOutput(logRedirect)
            .redirectError(errorRedirect)
            .start()

        logger.debug { "$binaryName process started (pid ${process.pid()})" }

        // Tracked until we've awaited teardown, so a JVM shutdown between start() and the final
        // waitFor() still gets the tree killed by ShellProcessTracker's hook.
        ShellProcessTracker.track(process)
        try {
            try {
                runInterruptible(Dispatchers.IO) {
                    process.waitFor()
                }
            } finally {
                withContext(NonCancellable) {
                    process.destroyTree()
                    process.waitFor()
                }
            }
        } finally {
            ShellProcessTracker.untrack(process)
        }

        val exitCode = process.exitValue()
        logger.info { "$binaryName exited with code $exitCode" }
        exitCode
    }

    /**
     * Default no-op availability check that subclasses may override. Kept as a convenience so that
     * `ShellBased*Executor` implementations not needing custom logic can just call through.
     */
    open fun isAvailable(): Boolean = probeAvailability(listOf("-V"))

    private fun buildProcess(args: List<String>): ProcessBuilder {
        val cmd = if (isWindows) listOf("cmd", "/c", binaryName) + args else listOf(binaryName) + args
        return ProcessBuilder(cmd)
    }

    private fun prepareOutputFiles(logFile: File?, errorFile: File?, header: String?) {
        logFile?.let { file ->
            file.ensureExists()
            file.bufferedWriter().use { writer ->
                if (header != null) writer.appendLine(header)
                writer.appendLine()
            }
        }
        errorFile?.let { it.ensureExists() }
    }

    private fun File.ensureExists(): File {
        parentFile?.mkdirs()
        createNewFile()
        return this
    }
}

/**
 * Destroys the process and every descendant launched by it. Necessary because launcher scripts
 * (e.g. nuXmv's shell wrapper) spawn child JVMs or solvers that outlive the immediate child.
 */
fun Process.destroyTree() {
    val handle = toHandle()
    handle.descendants().forEach { it.destroy() }
    handle.destroy()
}
