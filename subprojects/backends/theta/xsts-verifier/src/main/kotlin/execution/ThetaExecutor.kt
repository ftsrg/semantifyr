/*
 * SPDX-FileCopyrightText: 2023-2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.theta.verification.execution

import com.github.dockerjava.api.command.CreateContainerResponse
import com.github.dockerjava.api.exception.NotFoundException
import com.github.dockerjava.api.model.Bind
import com.github.dockerjava.api.model.HostConfig
import com.github.dockerjava.api.model.Volume
import com.github.dockerjava.api.model.WaitResponse
import com.github.dockerjava.core.DefaultDockerClientConfig
import com.github.dockerjava.core.DockerClientImpl
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient
import hu.bme.mit.semantifyr.semantics.transformation.ProgressContext
import jakarta.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.File
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.io.path.absolute
import kotlin.time.toDuration
import kotlin.time.toDurationUnit

class ThetaRuntimeDetails(
    val workingDirectory: String,
    val name: String,
    val id: Int,
) {
    val modelFileName = "$name.xsts"
    val cexFileName = "out.cex"
    val logFileName = "theta.out"
    val errFileName = "theta.err"

    val modelPath = modelFileName
    val artifactsPath = "verification${File.separator}artifacts$id"
    val cexPath = "$artifactsPath${File.separator}$cexFileName"
    val logPath = "$artifactsPath${File.separator}$logFileName"
    val errPath = "$artifactsPath${File.separator}$errFileName"
}

sealed class ThetaVerificationResult(
    val runtimeDetails: ThetaRuntimeDetails
) {
    val hasWitness = File(runtimeDetails.workingDirectory, runtimeDetails.cexPath).exists()
}
class ThetaUnknownVerificationResult(runtimeDetails: ThetaRuntimeDetails) : ThetaVerificationResult(runtimeDetails)
class ThetaSafeVerificationResult(runtimeDetails: ThetaRuntimeDetails) : ThetaVerificationResult(runtimeDetails)
class ThetaUnsafeVerificationResult(runtimeDetails: ThetaRuntimeDetails) : ThetaVerificationResult(runtimeDetails)
class ThetaErrorVerificationResult(runtimeDetails: ThetaRuntimeDetails) : ThetaVerificationResult(runtimeDetails) {
    val failureMessage = "Theta execution failed, see: ${runtimeDetails.workingDirectory}/${runtimeDetails.errPath}"
}

private fun ThetaRuntimeDetails.toVerificationResult(): ThetaVerificationResult {
    val errorFile = File(workingDirectory, errPath)
    if (errorFile.exists() && errorFile.useLines { it.any { it.isNotEmpty() } }) {
        return ThetaErrorVerificationResult(this)
    }

    val logFile = File(workingDirectory, logPath)
    if (logFile.exists()) {
        logFile.useLines { lines ->
            for (line in lines) {
                if (line.contains("SafetyResult Unsafe")) {
                    return ThetaUnsafeVerificationResult(this)
                }
                if (line.contains("SafetyResult Safe")) {
                    return ThetaSafeVerificationResult(this)
                }
            }
        }
    }

    return ThetaUnknownVerificationResult(this)
}

class DockerBasedThetaExecutor(
    val version: String,
    val timeout: Long = 3,
    val timeUnit: TimeUnit = TimeUnit.MINUTES
) {

    private val config = DefaultDockerClientConfig.createDefaultConfigBuilder().build()
    private val httpClient = ApacheDockerHttpClient.Builder()
        .dockerHost(config.dockerHost)
        .sslConfig(config.sslConfig).build()
    private val dockerClient = DockerClientImpl.getInstance(config, httpClient);

    fun initialize() {
        val imageId = "ftsrg/theta-xsts-cli:$version"
        try {
            dockerClient.inspectImageCmd(imageId).exec()
        } catch (e: NotFoundException) {
            dockerClient.pullImageCmd("ftsrg/theta-xsts-cli").withTag(version).start().awaitCompletion()
        }
    }

    suspend fun runTheta(
        workingDirectory: String,
        name: String,
        parameter: String,
        id: Int,
    ): ThetaVerificationResult {
        val thetaRuntimeDetails = ThetaRuntimeDetails(workingDirectory, name, id)

        val artifactsDir = File(thetaRuntimeDetails.workingDirectory, thetaRuntimeDetails.artifactsPath)
        artifactsDir.deleteRecursively()
        artifactsDir.mkdirs()

        val container = createContainer(thetaRuntimeDetails, parameter)

        dockerClient.startContainerCmd(container.id).exec()

        val result = try {
            withTimeout(timeout.toDuration(timeUnit.toDurationUnit())) {
                runAsync<WaitResponse>(Dispatchers.IO) {
                    dockerClient.waitContainerCmd(container.id).exec(it)
                }
            }
        } catch (e: TimeoutCancellationException) {
            throw e
        } catch (e: CancellationException) {
            throw e
        } finally {
            withContext(NonCancellable) {
                saveContainerLogs(thetaRuntimeDetails, container)

                dockerClient.removeContainerCmd(container.id).withForce(true).exec()
            }
        }

        if (result.statusCode != 0) {
            throw IllegalStateException("Theta execution failed with code ${result.statusCode}. See $thetaRuntimeDetails")
        }

        return thetaRuntimeDetails.toVerificationResult()
    }

    private suspend fun saveContainerLogs(
        thetaRuntimeDetails: ThetaRuntimeDetails,
        container: CreateContainerResponse
    ) {
        try {
            val logFile = File(thetaRuntimeDetails.workingDirectory, thetaRuntimeDetails.logPath)
            val errFile = File(thetaRuntimeDetails.workingDirectory, thetaRuntimeDetails.errPath)

            dockerClient.logContainerCmd(container.id)
                .withStdOut(true)
                .withStdErr(true)
                .withTailAll()
                .exec(StreamLoggerCallback(logFile.outputStream(), errFile.outputStream()))
                .await()
        } catch (t: Throwable) {
            // NO-OP
        }
    }

    private fun createContainer(
        thetaRuntimeDetails: ThetaRuntimeDetails,
        parameter: String
    ): CreateContainerResponse {
        val hostConfig = HostConfig.newHostConfig()
            .withBinds(Bind(thetaRuntimeDetails.workingDirectory, Volume("/host")))

        val container = dockerClient.createContainerCmd("ftsrg/theta-xsts-cli:$version")
            .withCmd(
                *parameter.split(" ").toTypedArray(),
                "--model", "/host/${thetaRuntimeDetails.modelPath}",
                "--cexfile", "/host/${thetaRuntimeDetails.cexPath}",
            )
            .withHostConfig(hostConfig)
            .exec()

        return container
    }

}

@Singleton
class ThetaPortfolioExecutor {
    val version = "6.27.0"
    val parameters = listOf(
        "CEGAR --domain EXPL --flatten-depth 0 --refinement SEQ_ITP --maxenum 250 --initprec CTRL --stacktrace",
        "CEGAR --domain EXPL_PRED_COMBINED --flatten-depth 0 --autoexpl NEWOPERANDS --initprec CTRL --stacktrace",
        "CEGAR --domain PRED_CART --flatten-depth 0 --refinement SEQ_ITP --stacktrace",
        "BOUNDED --flatten-depth 0 --variant KINDUCTION --stacktrace",
    )
    val limitedParallelism = 4
    val timeout = 5L
    val timeUnit = TimeUnit.MINUTES

    private val thetaDispatcher = Dispatchers.IO.limitedParallelism(limitedParallelism)
    private val thetaExecutor = DockerBasedThetaExecutor(version, timeout, timeUnit)

    fun initialize() {
        thetaExecutor.initialize()
    }

    private suspend fun runWorkflow(workingDirectory: String, name: String) = supervisorScope {
        val jobs = parameters.indices.map { index ->
            async(thetaDispatcher) {
                thetaExecutor.runTheta(workingDirectory, name, parameters[index], index)
            }
        }

        try {
            jobs.awaitAny()
        } finally {
            jobs.forEach {
                it.cancelAndJoin()
            }
        }
    }

    private fun CoroutineScope.startCancellationChecker(progressContext: ProgressContext): Deferred<Unit> {
        return async {
            while (true) {
                try {
                    progressContext.checkIsCancelled()
                } catch (c: CancellationException) {
                    this@startCancellationChecker.coroutineContext.cancel(c)
                }
                delay(100)
            }
        }
    }

    fun run(workingDirectory: String, name: String, progressContext: ProgressContext) = runBlocking {
        val absoluteDirectory = Path.of(workingDirectory).absolute().toString()

        val cancellationChecker = startCancellationChecker(progressContext)

        val result = runWorkflow(absoluteDirectory, name)

        cancellationChecker.cancel()

        result
    }

}
