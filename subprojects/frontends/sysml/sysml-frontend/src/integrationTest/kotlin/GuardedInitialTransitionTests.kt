/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.frontends.sysml

import hu.bme.mit.semantifyr.frontends.sysml.testing.CorpusCase
import hu.bme.mit.semantifyr.frontends.sysml.testing.CorpusEntry
import hu.bme.mit.semantifyr.frontends.sysml.testing.CorpusModel
import hu.bme.mit.semantifyr.frontends.sysml.testing.CorpusTier
import hu.bme.mit.semantifyr.frontends.sysml.testing.CorpusVerdict
import hu.bme.mit.semantifyr.frontends.sysml.testing.SysMLv2FrontendTestHelper
import hu.bme.mit.semantifyr.portfolios.Portfolios
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.copyTo
import kotlin.io.path.createDirectories

class GuardedInitialTransitionTests {
    companion object {
        private val helper = SysMLv2FrontendTestHelper()
        private val artifactRoot = SysMLv2FrontendTestHelper.testArtifactRoot(GuardedInitialTransitionTests::class.java)

        private const val MODEL_FILE = "guarded_initial_transition.sysml"

        // the SysML CLI reads every model next to the source, so the case gets a workspace of its own
        private val MODEL: Path by lazy {
            val workspace = artifactRoot.resolve("workspace").createDirectories()
            SysMLv2FrontendTestHelper.modelPath("Verification.sysml").copyTo(workspace.resolve("Verification.sysml"), overwrite = true)
            Path("..", "models", "etc", MODEL_FILE).copyTo(workspace.resolve(MODEL_FILE), overwrite = true)
        }

        // the model is a library fixture outside the corpus, so its cases are listed here
        private val CASES = listOf(
            "GuardedInitialTransitionEntersLowTestCase",
            "GuardedInitialTransitionNeverEntersHighTestCase",
            "GuardedNestedInitialTransitionEntersRightTestCase",
            "GuardedNestedInitialTransitionNeverEntersLeftTestCase",
        )

        @JvmStatic
        fun `Guarded Initial Transition Cases Should Pass`(): List<Arguments> {
            return SysMLv2Variant.entries.flatMap { variant ->
                val model = CorpusModel(
                    file = MODEL_FILE,
                    variant = variant,
                    cases = CASES.map {
                        CorpusCase(it, CorpusVerdict.Passed, CorpusTier.Integration)
                    },
                )
                model.cases.map {
                    Arguments.of(variant, CorpusEntry(model, it))
                }
            }
        }
    }

    @ParameterizedTest(name = "{0} - {1}")
    @MethodSource
    suspend fun `Guarded Initial Transition Cases Should Pass`(
        variant: SysMLv2Variant,
        entry: CorpusEntry,
    ) {
        val frontend = helper.buildFrontend(MODEL, Portfolios.SmartFull, artifactRoot.resolve(variant.name.lowercase()), variant)
        helper.checkConformance(frontend, entry)
    }
}
