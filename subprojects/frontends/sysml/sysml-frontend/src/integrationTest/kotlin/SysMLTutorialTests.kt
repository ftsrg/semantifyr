/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.frontends.sysml

import hu.bme.mit.semantifyr.backends.theta.ThetaBackend
import hu.bme.mit.semantifyr.backends.theta.ThetaConfig
import hu.bme.mit.semantifyr.frontends.sysml.testing.CorpusCase
import hu.bme.mit.semantifyr.frontends.sysml.testing.CorpusEntry
import hu.bme.mit.semantifyr.frontends.sysml.testing.CorpusModel
import hu.bme.mit.semantifyr.frontends.sysml.testing.CorpusTier
import hu.bme.mit.semantifyr.frontends.sysml.testing.CorpusVerdict
import hu.bme.mit.semantifyr.frontends.sysml.testing.SysMLv2FrontendTestHelper
import hu.bme.mit.semantifyr.verifier.portfolio.SingleBackendPortfolio
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import kotlin.io.path.Path
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.nameWithoutExtension

class SysMLTutorialTests {
    companion object {
        private val helper = SysMLv2FrontendTestHelper()
        private val artifactRoot = SysMLv2FrontendTestHelper.testArtifactRoot(SysMLTutorialTests::class.java)

        // the tutorial models are outside the corpus, so their cases come from discovery and the Violated suffix
        @JvmStatic
        fun `Tutorial Cases Verify As Named`(): List<Arguments> {
            return Path("..", "models", "tutorial").listDirectoryEntries("*.sysml").sorted().flatMap { source ->
                val frontend = helper.buildFrontend(
                    source,
                    SingleBackendPortfolio(ThetaBackend(), ThetaConfig.CegarExplPredCombined),
                    artifactRoot.resolve(source.nameWithoutExtension),
                )
                frontend.verificationCases().map {
                    val name = it.qualifiedName.substringAfterLast("::")
                    val verdict = if (name.endsWith("Violated")) CorpusVerdict.Failed else CorpusVerdict.Passed
                    val model = CorpusModel(source.name, cases = listOf(CorpusCase(name, verdict, CorpusTier.Integration)))
                    Arguments.of(CorpusEntry(model, model.cases.single()), frontend)
                }
            }
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource
    suspend fun `Tutorial Cases Verify As Named`(
        entry: CorpusEntry,
        frontend: SysMLv2Frontend,
    ) {
        helper.checkConformance(frontend, entry)
    }
}
