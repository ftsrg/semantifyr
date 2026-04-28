/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.live.backend.session

import hu.bme.mit.semantifyr.live.backend.BackendConfig
import hu.bme.mit.semantifyr.live.backend.SessionManagerConfig
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText

class WorkspaceSweeperTest {

    @TempDir
    lateinit var tempDir: Path

    private fun sweeperFor(root: Path): WorkspaceSweeper {
        val config = BackendConfig(
            sessionManager = SessionManagerConfig(rootWorkDirectory = root.toString()),
        )
        return WorkspaceSweeper(config)
    }

    @Test
    fun `no-op when sessions directory does not exist`() {
        sweeperFor(tempDir).sweep()
        assertThat(Files.exists(tempDir.resolve("sessions"))).isFalse()
    }

    @Test
    fun `removes leftover session workspaces`() {
        val sessionsRoot = tempDir.resolve("sessions").createDirectories()
        val stale1 = sessionsRoot.resolve("abc").createDirectories()
        stale1.resolve("file.txt").writeText("leftover")
        val stale2 = sessionsRoot.resolve("def").createDirectories()

        sweeperFor(tempDir).sweep()

        assertThat(Files.exists(stale1)).isFalse()
        assertThat(Files.exists(stale2)).isFalse()
        assertThat(Files.exists(sessionsRoot)).isTrue()
    }

    @Test
    fun `no-op when sessions directory is empty`() {
        val sessionsRoot = tempDir.resolve("sessions").createDirectories()
        sweeperFor(tempDir).sweep()
        assertThat(Files.exists(sessionsRoot)).isTrue()
    }
}
