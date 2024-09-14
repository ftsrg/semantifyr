/*
 * SPDX-FileCopyrightText: 2023-2024 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.TimeUnit
import kotlin.io.path.absolute

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
    val isSafe: Boolean = Files.exists(Paths.get(cexPath))
}

class ThetaExecutor(
    private val version: String = "latest",
    private val parameters: List<String>,
    private val timeout: Long = 60,
    private val timeUnit: TimeUnit = TimeUnit.MINUTES
) {

    private suspend fun runTheta(
        workingDirectory: String,
        name: String,
        parameter: String,
        id: Int
    ) = withContext(Dispatchers.IO) {
        run {
            val model = "$name.xsts"
            val property = "$name.prop"
            val cex = "$name$id.cex"
            val logName = "theta$id.out"
            val errName = "theta$id.err"

            val process = ProcessBuilder(
                "docker",
                "run",
                "--rm",
                "--mount", "type=bind,source=$workingDirectory,target=/host",
                "ftsrg/theta-xsts-cli:$version",
                "CEGAR",
                "--model", """"/host/$model"""",
                "--property", """"/host/$property"""",
                "--cexfile", """"/host/$cex"""",
                *parameter.split(" ").toTypedArray(),
            )
                .redirectOutput(File(workingDirectory, logName))
                .redirectError(File(workingDirectory, errName))
                .start()

            if (!process.waitFor(timeout, timeUnit)) {
                process.destroyForcibly()
            }

            ThetaExecutionResult(
                exitCode = process.waitFor(),
                id = id,
                modelPath = "$workingDirectory${File.separator}$model",
                propertyPath = "$workingDirectory${File.separator}$property",
                cexPath = "$workingDirectory${File.separator}$cex",
                logPath = "$workingDirectory${File.separator}$logName",
                errPath = "$workingDirectory${File.separator}$errName"
            )
        }
    }

    suspend fun runWorkflow(workingDirectory: String, name: String) = coroutineScope {
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
