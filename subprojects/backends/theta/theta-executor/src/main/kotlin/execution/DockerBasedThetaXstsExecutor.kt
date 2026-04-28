/*
 * SPDX-FileCopyrightText: 2025-2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.theta.execution

import com.github.dockerjava.api.command.CreateContainerResponse
import com.github.dockerjava.api.model.Bind
import com.github.dockerjava.api.model.HostConfig
import com.github.dockerjava.api.model.Volume
import hu.bme.mit.semantifyr.backend.execution.BaseDockerExecutor
import hu.bme.mit.semantifyr.backends.theta.ThetaExecutionResult
import hu.bme.mit.semantifyr.backends.theta.ThetaExecutionSpecification
import hu.bme.mit.semantifyr.backends.theta.ThetaExecutorSpec
import hu.bme.mit.semantifyr.backends.theta.ThetaXstsExecutor
import hu.bme.mit.semantifyr.logging.debug
import hu.bme.mit.semantifyr.logging.info
import hu.bme.mit.semantifyr.logging.loggerFactory

class DockerBasedThetaXstsExecutor(
    image: String = ThetaExecutorSpec.Docker.DEFAULT_IMAGE,
) : BaseDockerExecutor(image), ThetaXstsExecutor {
    override val logger by loggerFactory()

    override suspend fun execute(thetaExecutionSpecification: ThetaExecutionSpecification): ThetaExecutionResult {
        ensureImagePresent()
        prepareOutputFiles(thetaExecutionSpecification)

        val container = createContainer(thetaExecutionSpecification)
        logger.info { "Starting Docker container for image $image in ${thetaExecutionSpecification.workingDirectory.absolutePath}" }
        logger.debug { "Docker container id ${container.id}, command: ${thetaExecutionSpecification.command.joinToString(" ")}" }

        val result = runContainer(container, thetaExecutionSpecification.logFile, thetaExecutionSpecification.errorFile)

        logger.info { "Docker container exited with status ${result.statusCode}" }
        return ThetaExecutionResult(result.statusCode)
    }

    private fun createContainer(spec: ThetaExecutionSpecification): CreateContainerResponse {
        val hostConfig = HostConfig
            .newHostConfig()
            .withBinds(Bind(spec.workingDirectory.absolutePath, Volume("/host/working_directory")))

        return dockerClient
            .createContainerCmd(image)
            .withHostConfig(hostConfig)
            .withWorkingDir("/host/working_directory")
            .withCmd(spec.command)
            .withEntrypoint("java", "-jar", "/theta-xsts-cli.jar")
            .exec()
    }

    private fun prepareOutputFiles(spec: ThetaExecutionSpecification) {
        spec.logFile?.let {
            it.parentFile?.mkdirs()
            it.createNewFile()
            it.bufferedWriter().use { writer ->
                writer.appendLine("Running theta with command:")
                writer.appendLine(spec.command.joinToString(" "))
                writer.appendLine()
            }
            logger.info { "Writing theta stdout to ${it.absolutePath}" }
        }
        spec.errorFile?.let {
            it.parentFile?.mkdirs()
            it.createNewFile()
            logger.info { "Writing theta stderr to ${it.absolutePath}" }
        }
    }
}
