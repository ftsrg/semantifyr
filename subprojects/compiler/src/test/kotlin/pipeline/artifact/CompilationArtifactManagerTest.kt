/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler.pipeline.artifact

import hu.bme.mit.semantifyr.compiler.pipeline.CompilationRequest
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlinedOxsts
import org.eclipse.xtext.serializer.ISerializer
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.io.File
import java.io.Writer
import java.nio.file.Path

class CompilationArtifactManagerTest {
    @TempDir
    lateinit var tempDir: Path

    private val serializer: ISerializer = mock()
    private val inlinedOxsts: InlinedOxsts = mock()

    private fun managerWith(
        enabled: Set<ArtifactKind> = ArtifactKind.entries.toSet(),
        steps: CompilationStepsConfig = CompilationStepsConfig.All,
    ): CompilationArtifactManager {
        val config = ArtifactConfig(
            enabled = enabled,
            enabledCompilationSteps = steps,
        )
        val artifactManager: ArtifactManager = mock()
        whenever(artifactManager.withFile(any(), any())).thenAnswer {
            val kind = it.arguments[0] as ArtifactKind

            @Suppress("UNCHECKED_CAST")
            val block = it.arguments[1] as (File) -> Unit
            if (config.isEnabled(kind)) {
                val target = tempDir.resolve("${kind.name}.out").toFile()
                target.parentFile?.mkdirs()
                block(target)
            }
        }
        val request = CompilationRequest(inlinedOxsts = inlinedOxsts, outputDirectory = tempDir)
        return CompilationArtifactManager(serializer, artifactManager, config, request)
    }

    @Test
    fun `commitStep is a no-op when compilation steps are Off`() {
        val manager = managerWith(steps = CompilationStepsConfig.Off)

        manager.commitStep(CompilationPass.ConstantFolding)

        verify(serializer, never()).serialize(any(), any<Writer>(), any())
    }

    @Test
    fun `commitStep writes a file when steps are All`() {
        val manager = managerWith(steps = CompilationStepsConfig.All)

        manager.commitStep(CompilationPass.ConstantFolding)

        verify(serializer, times(1)).serialize(eq(inlinedOxsts), any<Writer>(), any())
    }

    @Test
    fun `commitStep respects a Selected steps filter`() {
        val manager = managerWith(
            steps = CompilationStepsConfig.Selected(setOf(CompilationPass.OperationFlattening)),
        )

        manager.commitStep(CompilationPass.OperationFlattening)
        manager.commitStep(CompilationPass.ConstantFolding)

        verify(serializer, times(1)).serialize(eq(inlinedOxsts), any<Writer>(), any())
    }

    @Test
    fun `commitStep increments step id so repeated passes write distinct files`() {
        val manager = managerWith(steps = CompilationStepsConfig.All)

        manager.commitStep(CompilationPass.ConstantFolding)
        manager.commitStep(CompilationPass.ConstantFolding)

        verify(serializer, times(2)).serialize(eq(inlinedOxsts), any<Writer>(), any())
    }

    @Test
    fun `commitInstantiated writes to the InstantiatedModel artifact`() {
        val manager = managerWith()

        manager.commitInstantiated()

        verify(serializer, times(1)).serialize(eq(inlinedOxsts), any<Writer>(), any())
    }

    @Test
    fun `commitInlined writes to the InlinedModel artifact`() {
        val manager = managerWith()

        manager.commitInlined()

        verify(serializer, times(1)).serialize(eq(inlinedOxsts), any<Writer>(), any())
    }

    @Test
    fun `commitFlattened writes to the FlattenedModel artifact`() {
        val manager = managerWith()

        manager.commitFlattened()

        verify(serializer, times(1)).serialize(eq(inlinedOxsts), any<Writer>(), any())
    }
}
