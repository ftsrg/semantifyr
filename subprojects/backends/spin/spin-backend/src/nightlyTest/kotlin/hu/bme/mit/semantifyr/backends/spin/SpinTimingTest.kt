/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.spin

import hu.bme.mit.semantifyr.backend.scopes.withVerificationScopeBlocking
import hu.bme.mit.semantifyr.backends.spin.transformation.SpinModelTransformer
import hu.bme.mit.semantifyr.backends.spin.verification.SpinBackendModule
import hu.bme.mit.semantifyr.compiler.SemantifyrCompiler
import hu.bme.mit.semantifyr.compiler.SysMLCrossroads
import hu.bme.mit.semantifyr.compiler.median
import hu.bme.mit.semantifyr.compiler.pipeline.artifact.ArtifactConfig
import hu.bme.mit.semantifyr.compiler.reader.SemantifyrLoader
import hu.bme.mit.semantifyr.compiler.warmRepetitions
import hu.bme.mit.semantifyr.oxsts.lang.OxstsStandaloneSetup
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import kotlin.io.path.Path
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.measureTime

// the limit is ten times the higher median of two runs at load 9 to 13 on 2026-11-01, so only a regression of an order of magnitude fails
class SpinTimingTest {

    private val injector = OxstsStandaloneSetup().createInjectorAndDoEMFRegistration()

    @Test
    fun `transforming crossroads to Promela stays within its limit`() {
        val caseClass = SysMLCrossroads.caseClass(injector.getInstance(SemantifyrLoader::class.java))
        val inlinedOxsts = SemantifyrCompiler(injector, ArtifactConfig.NONE).compile(caseClass, Path("build", "test-artifacts", "SpinTimingTest")).inlinedOxsts
        val spinInjector = injector.createChildInjector(SpinBackendModule())

        val durations = warmRepetitions {
            measureTime {
                withVerificationScopeBlocking {
                    spinInjector.getInstance(SpinModelTransformer::class.java).transform(inlinedOxsts)
                }
            }
        }

        assertThat(durations.median()).isLessThan(35.milliseconds)
    }
}
