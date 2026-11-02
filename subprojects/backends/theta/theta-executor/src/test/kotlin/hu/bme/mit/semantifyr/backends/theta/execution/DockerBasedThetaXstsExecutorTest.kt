/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.theta.execution

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class DockerBasedThetaXstsExecutorTest {

    @Test
    fun `THETA_JAVA_OPTS reaches the container's JVM`() {
        val executor = DockerBasedThetaXstsExecutor("image", thetaJavaOptions("-Xss512M  -Xmx1500M"))

        assertThat(executor.entrypoint).containsExactly("java", "-Xss512M", "-Xmx1500M", "-jar", "/theta-xsts-cli.jar")
    }

    @Test
    fun `without THETA_JAVA_OPTS the container gets the launcher scripts' defaults`() {
        assertThat(thetaJavaOptions(null)).containsExactly("-Xss512M", "-Xms1G", "-Xmx4G")
        assertThat(thetaJavaOptions("")).containsExactly("-Xss512M", "-Xms1G", "-Xmx4G")
    }
}
