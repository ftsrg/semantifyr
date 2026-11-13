/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.frontends.sysml

import hu.bme.mit.semantifyr.frontends.sysml.testing.CorpusEntry
import hu.bme.mit.semantifyr.frontends.sysml.testing.SysMLCorpus
import hu.bme.mit.semantifyr.frontends.sysml.testing.SysMLv2FrontendTestHelper
import hu.bme.mit.semantifyr.portfolios.Portfolios
import hu.bme.mit.semantifyr.portfolios.testing.ExampleLanguage
import hu.bme.mit.semantifyr.portfolios.testing.ServedExamples
import hu.bme.mit.semantifyr.portfolios.testing.liveBackends
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import kotlin.io.path.name

class SysMLServedExampleTests {
    companion object {
        private val helper = SysMLv2FrontendTestHelper()
        private val artifactRoot = SysMLv2FrontendTestHelper.testArtifactRoot(SysMLServedExampleTests::class.java)

        @JvmStatic
        fun `Served Cases Reach Their Expected Verdict On Live's Backends`(): List<CorpusEntry> {
            val manifest = SysMLCorpus.load().entries
            return ServedExamples.load().of(ExampleLanguage.Sysml).flatMap { example ->
                example.cases.map { name ->
                    manifest.single {
                        it.model.file == example.path.name && it.case.name == name
                    }
                }
            }
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource
    suspend fun `Served Cases Reach Their Expected Verdict On Live's Backends`(entry: CorpusEntry) {
        val frontend = helper.buildFrontend(
            SysMLv2FrontendTestHelper.modelPath(entry.model.file),
            Portfolios.SmartFull,
            artifactRoot,
            entry.model.variant,
            environment = liveBackends,
        )
        helper.checkVerificationCase(frontend, entry)
    }
}
