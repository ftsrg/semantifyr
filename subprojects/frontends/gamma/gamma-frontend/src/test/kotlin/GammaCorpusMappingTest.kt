/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import com.google.inject.Inject
import hu.bme.mit.semantifyr.frontends.gamma.GammaCompiler
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.GammaModelPackage
import hu.bme.mit.semantifyr.frontends.gamma.lang.tests.InjectWithGamma
import hu.bme.mit.semantifyr.frontends.gamma.reader.GammaReader
import hu.bme.mit.semantifyr.frontends.gamma.testing.GammaCorpus
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

@InjectWithGamma
class GammaCorpusMappingTest {

    @Inject
    private lateinit var gammaReader: GammaReader

    @Inject
    private lateinit var gammaCompiler: GammaCompiler

    @Test
    fun `every corpus case file maps to OXSTS without a refusal`(@TempDir temporaryDirectory: Path) {
        val files = GammaCorpus.load(GammaCorpus.corpus).map {
            it.modelPath
        }.distinct()
        val roots = gammaReader.readGammaFiles(
            files.map {
                it.toFile()
            },
            GammaCorpus.corpus,
        )

        val refusals = files.zip(roots).mapIndexedNotNull { index, (file, root) ->
            runCatching {
                gammaCompiler.compileWithImports(root as GammaModelPackage, temporaryDirectory.resolve("$index"))
            }.exceptionOrNull()?.let {
                "$file: ${it.message}"
            }
        }

        assertThat(files).isNotEmpty
        assertThat(refusals).isEmpty()
    }
}
