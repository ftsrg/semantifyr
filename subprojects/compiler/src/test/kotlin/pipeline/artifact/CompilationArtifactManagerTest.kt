/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler.pipeline.artifact

import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlinedOxsts
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.eclipse.xtext.serializer.ISerializer
import java.io.File
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
            outputDirectory = tempDir,
            enabled = enabled,
            enabledCompilationSteps = steps,
        )
        val artifactManager: ArtifactManager = mock()
        whenever(artifactManager.withFile(any(), any())).thenAnswer { invocation ->
            val kind = invocation.arguments[0] as ArtifactKind
            @Suppress("UNCHECKED_CAST")
            val block = invocation.arguments[1] as (File) -> Unit
            if (config.isEnabled(kind)) {
                val target = tempDir.resolve("${kind.name}.out").toFile()
                target.parentFile?.mkdirs()
                block(target)
            }
        }
        return CompilationArtifactManager(serializer, artifactManager, config)
    }

    @Test
    fun `commitStep is a no-op when compilation steps are Off`() {
        val manager = managerWith(steps = CompilationStepsConfig.Off)
        manager.setTarget(inlinedOxsts)

        manager.commitStep(CompilationPass.ConstantFolding)

        verify(serializer, never()).serialize(any(), any<java.io.Writer>(), any())
    }

    @Test
    fun `commitStep writes a file when steps are All`() {
        val manager = managerWith(steps = CompilationStepsConfig.All)
        manager.setTarget(inlinedOxsts)

        manager.commitStep(CompilationPass.ConstantFolding)

        verify(serializer, times(1)).serialize(eq(inlinedOxsts), any<java.io.Writer>(), any())
    }

    @Test
    fun `commitStep respects a Selected steps filter`() {
        val manager = managerWith(
            steps = CompilationStepsConfig.Selected(setOf(CompilationPass.OperationFlattening)),
        )
        manager.setTarget(inlinedOxsts)

        manager.commitStep(CompilationPass.OperationFlattening)
        manager.commitStep(CompilationPass.ConstantFolding)

        verify(serializer, times(1)).serialize(eq(inlinedOxsts), any<java.io.Writer>(), any())
    }

    @Test
    fun `commitStep increments step id so repeated passes write distinct files`() {
        val manager = managerWith(steps = CompilationStepsConfig.All)
        manager.setTarget(inlinedOxsts)

        manager.commitStep(CompilationPass.ConstantFolding)
        manager.commitStep(CompilationPass.ConstantFolding)

        verify(serializer, times(2)).serialize(eq(inlinedOxsts), any<java.io.Writer>(), any())
    }

    @Test
    fun `commitInstantiated writes to the InflatedModel artifact`() {
        val manager = managerWith()
        manager.setTarget(inlinedOxsts)

        manager.commitInstantiated()

        verify(serializer, times(1)).serialize(eq(inlinedOxsts), any<java.io.Writer>(), any())
    }

    @Test
    fun `commitInlined writes to the InlinedModel artifact`() {
        val manager = managerWith()
        manager.setTarget(inlinedOxsts)

        manager.commitInlined()

        verify(serializer, times(1)).serialize(eq(inlinedOxsts), any<java.io.Writer>(), any())
    }

    @Test
    fun `commitFlattened writes to the DeflatedModel artifact`() {
        val manager = managerWith()
        manager.setTarget(inlinedOxsts)

        manager.commitFlattened()

        verify(serializer, times(1)).serialize(eq(inlinedOxsts), any<java.io.Writer>(), any())
    }

    @Test
    fun `commitStep serializes the currently targeted model even after setTarget is called again`() {
        val manager = managerWith(steps = CompilationStepsConfig.All)
        val first: InlinedOxsts = mock()
        val second: InlinedOxsts = mock()

        manager.setTarget(first)
        manager.commitStep(CompilationPass.ConstantFolding)
        manager.setTarget(second)
        manager.commitStep(CompilationPass.OperationFlattening)

        val targetCaptor = argumentCaptor<InlinedOxsts>()
        verify(serializer, times(2)).serialize(targetCaptor.capture(), any<java.io.Writer>(), any())
        assertThat(targetCaptor.allValues).containsExactly(first, second)
    }
}
