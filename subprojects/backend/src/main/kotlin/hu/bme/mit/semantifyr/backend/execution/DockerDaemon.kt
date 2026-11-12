/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backend.execution

import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.core.DefaultDockerClientConfig
import com.github.dockerjava.core.DockerClientImpl
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient
import hu.bme.mit.semantifyr.logging.debug
import hu.bme.mit.semantifyr.logging.loggerFactory
import java.time.Duration

class DockerDaemon(
    clientFactory: () -> DockerClient,
    private val reachabilityProbe: (DockerClient) -> Boolean,
) {

    val client: DockerClient by lazy {
        clientFactory()
    }

    val isReachable: Boolean by lazy {
        reachabilityProbe(client)
    }

    companion object {
        private val logger by loggerFactory()

        val Process = DockerDaemon(
            clientFactory = ::createClient,
            reachabilityProbe = ::servesLinuxContainers,
        )

        private fun createClient(): DockerClient {
            val config = DefaultDockerClientConfig.createDefaultConfigBuilder().build()
            val httpClient = ApacheDockerHttpClient.Builder()
                .dockerHost(config.dockerHost)
                .sslConfig(config.sslConfig)
                // the default of three minutes is spent spinning on a Windows host whose Docker is not running
                .connectionTimeout(Duration.ofSeconds(10))
                .build()
            return DockerClientImpl.getInstance(config, httpClient)
        }

        private fun servesLinuxContainers(client: DockerClient): Boolean {
            val osType = try {
                client.infoCmd().exec().osType
            } catch (throwable: Throwable) {
                logger.debug(throwable) { "Docker daemon is unreachable." }

                return false
            }

            if (osType != "linux") {
                logger.debug { "Docker daemon runs $osType containers, the backend images need linux" }
                return false
            }

            logger.debug { "Docker daemon reachable" }
            return true
        }
    }
}
