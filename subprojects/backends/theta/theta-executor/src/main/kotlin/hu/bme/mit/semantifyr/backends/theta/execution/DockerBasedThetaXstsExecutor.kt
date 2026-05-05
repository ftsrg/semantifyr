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
import hu.bme.mit.semantifyr.backend.execution.DockerBasedBackendExecutor
import hu.bme.mit.semantifyr.backends.theta.THETA_DEFAULT_IMAGE
import hu.bme.mit.semantifyr.backends.theta.ThetaExecutionResult
import hu.bme.mit.semantifyr.backends.theta.ThetaExecutionSpecification
import hu.bme.mit.semantifyr.backends.theta.ThetaXstsExecutor
import hu.bme.mit.semantifyr.logging.debug
import hu.bme.mit.semantifyr.logging.info

class DockerBasedThetaXstsExecutor(
    image: String = THETA_DEFAULT_IMAGE,
) : DockerBasedBackendExecutor(image),
    ThetaXstsExecutor {

    override suspend fun execute(thetaExecutionSpecification: ThetaExecutionSpecification): ThetaExecutionResult {
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
}
