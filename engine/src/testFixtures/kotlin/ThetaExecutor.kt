/*
 * SPDX-FileCopyrightText: 2023-2024 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.TimeUnit
import kotlin.io.path.absolute
import kotlin.time.Duration
import kotlin.time.toDuration
import kotlin.time.toDurationUnit

suspend fun <T> List<Deferred<T>>.awaitAny(): T {
    val firstCompleted = CompletableDeferred<T>()

    forEach { job ->
        job.invokeOnCompletion { exception ->
            if (exception == null && !firstCompleted.isCompleted) {
                firstCompleted.complete(job.getCompleted())
            }
        }
    }

    return firstCompleted.await()
}

class ThetaExecutionResult(
    val exitCode: Int,
    val id: Int,
    val modelPath: String,
    val propertyPath: String,
    val cexPath: String,
    val logPath: String,
    val errPath: String
) {
    val isUnsafe: Boolean = Files.exists(Paths.get(cexPath))
}

class ThetaExecutor(
    private val version: String = "latest",
    private val parameters: List<String>,
    private val timeout: Long = 5,
    private val timeUnit: TimeUnit = TimeUnit.MINUTES
) {

    val logger = LoggerFactory.getLogger(javaClass)

    fun initTheta() {
        val process = ProcessBuilder(
            "docker",
            "pull",
            "ftsrg/theta-xsts-cli:$version",
        )
            .inheritIO()
            .start()

        process.waitFor()
    }

    private suspend fun runTheta(
        workingDirectory: String,
        name: String,
        parameter: String,
        id: Int
    ) = withContext(Dispatchers.IO) {
        val model = "$name.xsts"
        val property = "$name.prop"
        val cex = "$name$id.cex"
        val logName = "theta$id.out"
        val errName = "theta$id.err"

        val process = ProcessBuilder(
            "docker",
            "run",
            "--rm",
            "-v", "$workingDirectory:/host",
            "ftsrg/theta-xsts-cli:$version",
            "CEGAR",
            "--model", "/host/$model",
            "--property", "/host/$property",
            "--cexfile", "/host/$cex",
            *parameter.split(" ").toTypedArray(),
        )
            .redirectOutput(File(workingDirectory, logName))
            .redirectError(File(workingDirectory, errName))
            .start()

        val exitCode = try {
            withTimeout(timeout.toDuration(timeUnit.toDurationUnit())) {
                runInterruptible {
                    process.waitFor()
                }
            }
        } catch (e: TimeoutCancellationException) {
            -1
        } catch (e: CancellationException) {
            -2
        } finally {
            process.destroyForcibly()
        }

        val result = ThetaExecutionResult(
            exitCode = exitCode,
            id = id,
            modelPath = "$workingDirectory${File.separator}$model",
            propertyPath = "$workingDirectory${File.separator}$property",
            cexPath = "$workingDirectory${File.separator}$cex",
            logPath = "$workingDirectory${File.separator}$logName",
            errPath = "$workingDirectory${File.separator}$errName"
        )

        when (result.exitCode) {
            0 -> logger.info("Theta ($id) finished successfully!")
            -1 -> logger.info("Theta ($id) timed out!")
            -2 -> logger.info("Theta ($id) has been cancelled!")
            else -> logger.error("Theta ($id) failed execution:\n" + File(result.errPath).readText())
        }

        result
    }

    private suspend fun runWorkflow(workingDirectory: String, name: String) = coroutineScope {
        val jobs = parameters.indices.map { index ->
            async {
                runTheta(workingDirectory, name, parameters[index], index)
            }
        }

        val finishedJob = jobs.awaitAny()

        jobs.forEach {
            it.cancelAndJoin()
        }

        finishedJob
    }

    fun run(workingDirectory: String, name: String) = runBlocking {
        val absoluteDirectory = Path.of(workingDirectory).absolute().toString()

        runWorkflow(absoluteDirectory, name)
    }

}
