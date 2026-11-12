/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backend.execution

import com.github.dockerjava.api.DockerClient
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.lang.reflect.Proxy
import java.util.concurrent.atomic.AtomicInteger

class DockerBasedBackendExecutorTest {

    private class TestExecutor(
        image: String,
        daemon: DockerDaemon,
    ) : DockerBasedBackendExecutor(image, daemon)

    @Test
    fun `the daemon is probed once across executors`() {
        val clients = AtomicInteger()
        val probes = AtomicInteger()
        val daemon = DockerDaemon(
            clientFactory = {
                clients.incrementAndGet()
                Proxy.newProxyInstance(DockerClient::class.java.classLoader, arrayOf(DockerClient::class.java)) { _, method, _ ->
                    error("unexpected call to ${method.name}")
                } as DockerClient
            },
            reachabilityProbe = {
                probes.incrementAndGet()
                false
            },
        )
        val executors = listOf(
            TestExecutor("first:1", daemon),
            TestExecutor("second:1", daemon),
        )

        repeat(2) {
            executors.forEach {
                assertThat(it.isAvailable()).isFalse()
            }
        }

        assertThat(probes.get()).isEqualTo(1)
        assertThat(clients.get()).isEqualTo(1)
    }
}
