/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.frontends.gamma.semantics

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
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import java.util.stream.Stream
import kotlin.io.path.Path
import kotlin.streams.asStream

@Tag("verification")
@InjectWithOxstsSemantics
class GammaVerificationTests : BaseSemantifyrVerificationTest<ThetaVerifier>() {



    companion object {

        private val semantifyrVerificationHelper = StandaloneOxstsSemanticsRuntimeModule.getInstance<SemantifyrVerificationHelper>()

        @JvmStatic
        fun `Gamma Semantic Library Verification Cases Should Pass`(): Stream<Arguments> {
            val model = semantifyrVerificationHelper.semantifyrLoader.startContext()
                .loadModels(Path("Library"))
                .buildAndResolve()

            return semantifyrVerificationHelper.collectNotSlowVerificationCases(model).asStream()
        }

        val transformer = StandaloneGammaTransformer()

        fun loadModel(model: String): SemantifyrModelContext {
            val gammaModelPath = Path(model)
            val gammaModel = gammaModelPath.toFile()
            val oxstsModelPath = Path(gammaModel.absolutePath.replace(".gamma", ".oxsts"))
            val oxstsModel = oxstsModelPath.toFile()

            transformer.transformModel(gammaModel, oxstsModel)

            return semantifyrVerificationHelper.semantifyrLoader.startContext()
                .loadLibraries(Path("Library"))
                .loadModel(oxstsModelPath)
                .buildAndResolve()
        }

        @JvmStatic
        fun `Simple Model Verification Cases Should Pass`(): Stream<Arguments> {
            val model = loadModel("TestModels/Simple.gamma")

            return semantifyrVerificationHelper.collectNotSlowVerificationCases(model).asStream()
        }

        @JvmStatic
        fun `Crossroads Model Verification Cases Should Pass`(): Stream<Arguments> {
            val model = loadModel("TestModels/Crossroads.gamma")

            return semantifyrVerificationHelper.collectNotSlowVerificationCases(model).asStream()
        }

        @JvmStatic
        fun `Spacecraft Model Verification Cases Should Pass`(): Stream<Arguments> {
            val model = loadModel("TestModels/Spacecraft.gamma")

            return semantifyrVerificationHelper.collectNotSlowVerificationCases(model).asStream()
        }

        @JvmStatic
        fun `Slow Spacecraft Model Verification Cases Should Pass`(): Stream<Arguments> {
            val model = loadModel("TestModels/Spacecraft.gamma")

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
    fun `Gamma Semantic Library Verification Cases Should Pass`(verificationCase: ClassDeclaration) {
        checkVerificationCase(verificationCase)
    }

    @ParameterizedTest
    @MethodSource
    fun `Simple Model Verification Cases Should Pass`(verificationCase: ClassDeclaration) {
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
    @Disabled("There are no slow crossroads verification cases just yet.")
    fun `Slow Spacecraft Model Verification Cases Should Pass`(verificationCase: ClassDeclaration) {
        checkVerificationCase(verificationCase, 30, TimeUnit.MINUTES)
    }

}
