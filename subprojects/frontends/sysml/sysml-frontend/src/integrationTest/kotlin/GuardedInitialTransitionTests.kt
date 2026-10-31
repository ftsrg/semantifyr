/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.frontends.sysml

import hu.bme.mit.semantifyr.frontends.sysml.testing.SysMLv2FrontendTestHelper
import hu.bme.mit.semantifyr.portfolios.Portfolios
import hu.bme.mit.semantifyr.verifier.VerificationCase
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.nio.file.Path
import java.util.stream.Stream
import kotlin.io.path.Path
import kotlin.io.path.copyTo
import kotlin.io.path.createDirectories

class GuardedInitialTransitionTests {
    companion object {
        private val helper = SysMLv2FrontendTestHelper()
        private val artifactRoot = SysMLv2FrontendTestHelper.testArtifactRoot(GuardedInitialTransitionTests::class.java)

        // the SysML CLI reads every model next to the source, so the case gets a workspace of its own
        private val MODEL: Path by lazy {
            val workspace = artifactRoot.resolve("workspace").createDirectories()
            SysMLv2FrontendTestHelper.modelPath("Verification.sysml").copyTo(workspace.resolve("Verification.sysml"), overwrite = true)
            Path("..", "models", "etc", "guarded_initial_transition.sysml").copyTo(workspace.resolve("guarded_initial_transition.sysml"), overwrite = true)
        }

        @JvmStatic
        fun `Guarded Initial Transition Cases Should Pass`(): Stream<Arguments> {
            return helper.discoverAsArguments(MODEL, Portfolios.SmartFull, artifactRoot, SysMLv2Variant.entries)
        }
    }

    @ParameterizedTest(name = "{0} - {1}")
    @MethodSource
    suspend fun `Guarded Initial Transition Cases Should Pass`(
        variant: SysMLv2Variant,
        verificationCase: VerificationCase,
    ) {
        val frontend = helper.buildFrontend(MODEL, Portfolios.SmartFull, artifactRoot, variant)
        helper.checkConformance(
            frontend = frontend,
            case = verificationCase,
        )
    }
}
