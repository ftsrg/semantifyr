/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.frontends.sysml

import hu.bme.mit.semantifyr.backends.theta.ThetaBackend
import hu.bme.mit.semantifyr.backends.theta.ThetaConfig
import hu.bme.mit.semantifyr.frontends.sysml.testing.CorpusEntry
import hu.bme.mit.semantifyr.frontends.sysml.testing.SysMLv2FrontendTestHelper
import hu.bme.mit.semantifyr.verifier.portfolio.SingleBackendPortfolio
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import kotlin.io.path.nameWithoutExtension

class SysMLTutorialTests {
    companion object {
        private val helper = SysMLv2FrontendTestHelper()
        private val artifactRoot = SysMLv2FrontendTestHelper.testArtifactRoot(SysMLTutorialTests::class.java)

        @JvmStatic
        fun `Tutorial Cases Reach Their Expected Verdict`(): List<Arguments> {
            return SysMLv2FrontendTestHelper.tutorialEntries.groupBy {
                it.model
            }.flatMap { (model, entries) ->
                val source = SysMLv2FrontendTestHelper.TUTORIAL_ROOT.resolve(model.file)
                val frontend = helper.buildFrontend(
                    source,
                    SingleBackendPortfolio(ThetaBackend(), ThetaConfig.CegarExplPredCombined),
                    artifactRoot.resolve(source.nameWithoutExtension),
                )
                entries.map {
                    Arguments.of(it, frontend)
                }
            }
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource
    suspend fun `Tutorial Cases Reach Their Expected Verdict`(
        entry: CorpusEntry,
        frontend: SysMLv2Frontend,
    ) {
        helper.checkConformance(frontend, entry)
    }
}
