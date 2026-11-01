/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.theta

import hu.bme.mit.semantifyr.backend.BackendVerificationRequest
import hu.bme.mit.semantifyr.backend.VerificationVerdict
import hu.bme.mit.semantifyr.backend.execution.ExecutionEnvironment
import hu.bme.mit.semantifyr.backend.scopes.withVerificationScope
import hu.bme.mit.semantifyr.backends.theta.backannotation.CexReader
import hu.bme.mit.semantifyr.backends.theta.backannotation.CexWitnessTransformer
import hu.bme.mit.semantifyr.backends.theta.backannotation.InlinedWitnessTransformer
import hu.bme.mit.semantifyr.backends.theta.backannotation.XstsWitnessTransformer
import hu.bme.mit.semantifyr.backends.theta.transformation.xsts.ThetaModelTransformer
import hu.bme.mit.semantifyr.compiler.SemantifyrCompiler
import hu.bme.mit.semantifyr.compiler.SysMLCrossroads
import hu.bme.mit.semantifyr.compiler.median
import hu.bme.mit.semantifyr.compiler.pipeline.artifact.ArtifactConfig
import hu.bme.mit.semantifyr.compiler.reader.SemantifyrLoader
import hu.bme.mit.semantifyr.compiler.warmRepetitions
import hu.bme.mit.semantifyr.oxsts.lang.OxstsStandaloneSetup
import hu.bme.mit.semantifyr.verifier.assumeBackendAvailable
import org.assertj.core.api.Assertions.assertThat
import org.eclipse.emf.common.util.URI
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.createDirectories
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.measureTime

// the limit is ten times the higher median of two runs at load 9 to 13 on 2026-11-01, so only a regression of an order of magnitude fails
class ThetaWitnessTimingTest {

    companion object {
        @JvmStatic
        @BeforeAll
        fun assumeThetaAvailable() {
            assumeBackendAvailable(ThetaExecutorKey)
        }
    }

    private val injector = OxstsStandaloneSetup().createInjectorAndDoEMFRegistration()

    @Test
    suspend fun `back-annotating Theta's crossroads witness stays within its limit`() {
        val outputDirectory = Path("build", "test-artifacts", "ThetaWitnessTimingTest")
        val caseClass = SysMLCrossroads.caseClass(injector.getInstance(SemantifyrLoader::class.java))
        val inlinedOxsts = SemantifyrCompiler(injector, ArtifactConfig.NONE).compile(caseClass, outputDirectory).inlinedOxsts
        val thetaDirectory = outputDirectory.resolve("theta").createDirectories()
        val result = ThetaBackend().verify(injector, ThetaConfig.CegarExpl, BackendVerificationRequest(inlinedOxsts, thetaDirectory), ExecutionEnvironment.Empty)
        assertThat(result.verdict).isEqualTo(VerificationVerdict.Failed)

        val thetaInjector = injector.createChildInjector(ThetaBackendModule())
        val xsts = withVerificationScope {
            thetaInjector.getInstance(ThetaModelTransformer::class.java).transform(inlinedOxsts, URI.createFileURI(thetaDirectory.resolve("timed.xsts").absolutePathString()))
        }
        val durations = warmRepetitions {
            measureTime {
                val witness = withVerificationScope {
                    val cexModel = thetaInjector.getInstance(CexReader::class.java).loadCexModel(thetaDirectory.resolve("out.cex"))
                    val cexWitness = thetaInjector.getInstance(CexWitnessTransformer::class.java).transform(cexModel)
                    val xstsWitness = thetaInjector.getInstance(XstsWitnessTransformer::class.java).transform(xsts, cexWitness)
                    thetaInjector.getInstance(InlinedWitnessTransformer::class.java).transform(inlinedOxsts, xstsWitness)
                }
                assertThat(witness.transitionStates).isNotEmpty()
            }
        }

        assertThat(durations.median()).isLessThan(300.milliseconds)
    }
}
