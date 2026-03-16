/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.frontends.sysml.semantics

import com.google.inject.Inject
import com.google.inject.Provider
import hu.bme.mit.semantifyr.backends.theta.verification.ThetaVerifier
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ClassDeclaration
import hu.bme.mit.semantifyr.semantics.BaseSemantifyrVerificationTest
import hu.bme.mit.semantifyr.semantics.InjectWithOxstsSemantics
import hu.bme.mit.semantifyr.semantics.SemantifyrVerificationHelper
import hu.bme.mit.semantifyr.semantics.StandaloneOxstsSemanticsRuntimeModule
import hu.bme.mit.semantifyr.semantics.reader.SemantifyrLoader
import hu.bme.mit.semantifyr.semantics.reader.SemantifyrModelContext
import hu.bme.mit.semantifyr.semantics.utils.loggerFactory
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Tag
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.concurrent.TimeUnit
import java.util.stream.Stream
import kotlin.io.path.Path
import kotlin.streams.asStream

@Tag("verification")
@InjectWithOxstsSemantics
class SysMLVerificationTests : BaseSemantifyrVerificationTest<ThetaVerifier>() {

    companion object {

        private val semantifyrVerificationHelper = StandaloneOxstsSemanticsRuntimeModule.getInstance<SemantifyrVerificationHelper>()

        @JvmStatic
        fun `SysML Semantic Library Verification Cases Should Pass`(): Stream<Arguments> {
            val model = semantifyrVerificationHelper.semantifyrLoader.startContext()
                .loadModels(Path("Library"))
                .buildAndResolve()

            return semantifyrVerificationHelper.collectNotSlowVerificationCases(model).asStream()
        }

        private fun loadModel(path: String): SemantifyrModelContext {
            return semantifyrVerificationHelper.semantifyrLoader.startContext()
                .loadLibraries(Path("Library"))
                .loadModel(Path(path))
                .buildAndResolve()
        }

        @JvmStatic
        fun `STM21 Model Verification Cases Should Pass`(): Stream<Arguments> {
            val model = loadModel("models/stm21.oxsts")

            return semantifyrVerificationHelper.collectNotSlowVerificationCases(model).asStream()
        }

        @JvmStatic
        fun `STM31 Model Verification Cases Should Pass`(): Stream<Arguments> {
            val model = loadModel("models/stm31.oxsts")

            return semantifyrVerificationHelper.collectNotSlowVerificationCases(model).asStream()
        }

        @JvmStatic
        fun `Crossroads Model Verification Cases Should Pass`(): Stream<Arguments> {
            val model = loadModel("models/crossroads.oxsts")

            return semantifyrVerificationHelper.collectNotSlowVerificationCases(model).asStream()
        }

        @JvmStatic
        fun `Spacecraft Model Verification Cases Should Pass`(): Stream<Arguments> {
            val model = loadModel("models/spacecraft.oxsts")

            return semantifyrVerificationHelper.collectNotSlowVerificationCases(model).asStream()
        }

        @JvmStatic
        fun `Slow Spacecraft Model Verification Cases Should Pass`(): Stream<Arguments> {
            val model = loadModel("models/spacecraft.oxsts")

            return semantifyrVerificationHelper.collectSlowVerificationCases(model).asStream()
        }

    }

    override val logger by loggerFactory()

    @Inject
    lateinit var semantifyrLoader: SemantifyrLoader

    @Inject
    override lateinit var oxstsVerifierProvider: Provider<ThetaVerifier>

    @ParameterizedTest
    @MethodSource
    @Disabled("There are no semantic library verification cases just yet.")
    fun `SysML Semantic Library Verification Cases Should Pass`(verificationCase: ClassDeclaration) {
        checkVerificationCase(verificationCase)
    }

    @ParameterizedTest
    @MethodSource
    fun `STM21 Model Verification Cases Should Pass`(verificationCase: ClassDeclaration) {
        checkVerificationCase(verificationCase)
    }

    @ParameterizedTest
    @MethodSource
    fun `STM31 Model Verification Cases Should Pass`(verificationCase: ClassDeclaration) {
        checkVerificationCase(verificationCase)
    }

    @ParameterizedTest
    @MethodSource
    fun `Crossroads Model Verification Cases Should Pass`(verificationCase: ClassDeclaration) {
        checkVerificationCase(verificationCase)
    }

    @ParameterizedTest
    @MethodSource
    fun `Spacecraft Model Verification Cases Should Pass`(verificationCase: ClassDeclaration) {
        checkVerificationCase(verificationCase)
    }

    @Tag("slow")
    @ParameterizedTest
    @MethodSource
    fun `Slow Spacecraft Model Verification Cases Should Pass`(verificationCase: ClassDeclaration) {
        checkVerificationCase(verificationCase, 30, TimeUnit.MINUTES)
    }

}
