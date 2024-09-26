/*
 * SPDX-FileCopyrightText: 2023-2024 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.compiler

import com.github.dockerjava.api.command.CreateContainerResponse
import com.github.dockerjava.api.exception.ConflictException
import com.github.dockerjava.api.model.Bind
import com.github.dockerjava.api.model.HostConfig
import com.github.dockerjava.api.model.Volume
import com.github.dockerjava.api.model.WaitResponse
import com.github.dockerjava.core.DefaultDockerClientConfig
import com.github.dockerjava.core.DockerClientImpl
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withTimeout
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.TimeUnit
import kotlin.io.path.absolute
import kotlin.time.toDuration
import kotlin.time.toDurationUnit

class ThetaExecutionResult(
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
    private val timeout: Long = 10,
    private val timeUnit: TimeUnit = TimeUnit.MINUTES
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    private val config = DefaultDockerClientConfig.createDefaultConfigBuilder().build()
    private val httpClient = ApacheDockerHttpClient.Builder()
        .dockerHost(config.getDockerHost())
        .sslConfig(config.getSSLConfig()).build()
    private val dockerClient = DockerClientImpl.getInstance(config, httpClient);

    fun initTheta() {
        dockerClient.pullImageCmd("ftsrg/theta-xsts-cli").withTag(version).start().awaitCompletion()
    }

    private suspend fun runTheta(
        workingDirectory: String,
        name: String,
        parameter: String,
        id: Int
    ): ThetaExecutionResult {
        val model = "$name.xsts"
        val property = "$name.prop"
        val cex = "$name$id.cex"
        val logName = "theta$id.out"
        val errName = "theta$id.err"

        logger.info("Starting container ($id)")

        val container = createContainer(logName, errName, workingDirectory, model, property, cex, parameter)
        dockerClient.startContainerCmd(container.id).exec()

        val result = try {
            withTimeout(timeout.toDuration(timeUnit.toDurationUnit())) {
                runAsync<WaitResponse> {
                    dockerClient.waitContainerCmd(container.id).exec(it)
                }
            }
        } catch (e: TimeoutCancellationException) {
            logger.info("Theta timed out ($id)")
            throw e
        } catch (e: CancellationException) {
            logger.info("Theta cancelled ($id)")
            throw e
        } finally {
            try {
                logger.info("Removing container ($id)")
                dockerClient.removeContainerCmd(container.id).withForce(true).exec()
            } catch (e: ConflictException) {
                // ignore, it means the container is already being removed
            }
        }

        if (result.statusCode == 0) {
            logger.info("Theta finished ($id)")
        } else {
            logger.error("Theta failed ($id)")
            throw IllegalStateException("Theta execution failed with code ${result.statusCode}. See $workingDirectory${File.separator}$errName")
        }

        return ThetaExecutionResult(
            id = id,
            modelPath = "$workingDirectory${File.separator}$model",
            propertyPath = "$workingDirectory${File.separator}$property",
            cexPath = "$workingDirectory${File.separator}$cex",
            logPath = "$workingDirectory${File.separator}$logName",
            errPath = "$workingDirectory${File.separator}$errName",
        )
    }

    private fun createContainer(
        logName: String,
        errName: String,
        workingDirectory: String,
        model: String,
        property: String,
        cex: String,
        parameter: String
    ): CreateContainerResponse {
        val logFile = File(workingDirectory, logName)
        val errFile = File(workingDirectory, errName)

        val hostConfig = HostConfig.newHostConfig()
            .withBinds(Bind(workingDirectory, Volume("/host")))

        val container = dockerClient.createContainerCmd("ftsrg/theta-xsts-cli:$version")
            .withCmd(
                "CEGAR",
                "--model", "/host/$model",
                "--property", "/host/$property",
                "--cexfile", "/host/$cex",
                *parameter.split(" ").toTypedArray(),
            )
            .withHostConfig(hostConfig)
            .exec()

        dockerClient.logContainerCmd(container.id)
            .withStdOut(true)
            .withStdErr(true)
            .exec(StreamLoggerCallback(logFile.outputStream(), errFile.outputStream()))

        return container
    }

    private suspend fun runWorkflow(workingDirectory: String, name: String) = coroutineScope {
        val jobs = supervisorScope {
            parameters.indices.map { index ->
                async(Dispatchers.IO) {
                    runTheta(workingDirectory, name, parameters[index], index)
                }
            }
        }

        try {
            logger.debug("Awaiting jobs")
            jobs.awaitAny()
        } finally {
            logger.debug("Canceling jobs")
            jobs.forEach {
                it.cancelAndJoin()
            }
        }
    }

    fun run(workingDirectory: String, name: String) = runBlocking {
        val absoluteDirectory = Path.of(workingDirectory).absolute().toString()

        runWorkflow(absoluteDirectory, name)
    }

}
