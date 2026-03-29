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
import hu.bme.mit.semantifyr.semantics.reader.SemantifyrModelContext
import hu.bme.mit.semantifyr.semantics.utils.loggerFactory
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Tag
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream
import kotlin.io.path.Path
import kotlin.streams.asStream
import kotlin.time.DurationUnit
import kotlin.time.toDuration

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

            return semantifyrVerificationHelper.collectVerificationCases(model).asStream()
        }

        val transformer = StandaloneSysMLTransformer()

        private fun loadModel(model: String): SemantifyrModelContext {
            val sysmlModelPath = Path(model)
            val sysmlModel = sysmlModelPath.toFile()
            val oxstsModelPath = Path(sysmlModel.absolutePath.replace(".sysml", ".oxsts"))
            val oxstsModel = oxstsModelPath.toFile()

            transformer.transformModel(sysmlModel, oxstsModel)

            return semantifyrVerificationHelper.semantifyrLoader.startContext()
                .loadLibraries(Path("Library"))
                .loadModel(oxstsModelPath)
                .buildAndResolve()
        }

        @JvmStatic
        fun `STM21 Model Verification Cases Should Pass`(): Stream<Arguments> {
            val model = loadModel("TestModels/stm21.sysml")

            return semantifyrVerificationHelper.collectVerificationCases(model).asStream()
        }

        @JvmStatic
        fun `STM31 Model Verification Cases Should Pass`(): Stream<Arguments> {
            val model = loadModel("TestModels/stm31.sysml")

            return semantifyrVerificationHelper.collectVerificationCases(model).asStream()
        }

        @JvmStatic
        fun `Semantics Test Model Verification Cases Should Pass`(): Stream<Arguments> {
            val model = loadModel("TestModels/semanticstest.sysml")

            return semantifyrVerificationHelper.collectNotSlowVerificationCases(model).asStream()
            return semantifyrVerificationHelper.collectVerificationCases(model).asStream()
        }

        @JvmStatic
        fun `Crossroads Model Verification Cases Should Pass`(): Stream<Arguments> {
            val model = loadModel("TestModels/crossroads.sysml")

            return semantifyrVerificationHelper.collectVerificationCases(model).asStream()
        }

        @JvmStatic
        fun `Compressed Spacecraft Model Verification Cases Should Pass`(): Stream<Arguments> {
            val model = loadModel("TestModels/compressedspacecraft.sysml")

            return semantifyrVerificationHelper.collectVerificationCases(model).asStream()
        }

        @JvmStatic
        fun `Full Spacecraft Model Verification Cases Should Pass`(): Stream<Arguments> {
            val model = loadModel("TestModels/spacecraft.sysml")

            return semantifyrVerificationHelper.collectVerificationCases(model).asStream()
        }

        @JvmStatic
        fun `Door Access Model Verification Cases Should Pass`(): Stream<Arguments> {
            val model = loadModel("TestModels/door_access.sysml")

            return semantifyrVerificationHelper.collectVerificationCases(model).asStream()
        }

        @JvmStatic
        fun `Power Subsystems Model Verification Cases Should Pass`(): Stream<Arguments> {
            val model = loadModel("TestModels/power_subsystems.sysml")

            return semantifyrVerificationHelper.collectVerificationCases(model).asStream()
        }

        @JvmStatic
        fun `Aircraft Engine Model Verification Cases Should Pass`(): Stream<Arguments> {
            val model = loadModel("TestModels/aircraft_engine.sysml")

            return semantifyrVerificationHelper.collectVerificationCases(model).asStream()
        }

        @JvmStatic
        fun `Autonomous Driving Model Verification Cases Should Pass`(): Stream<Arguments> {
            val model = loadModel("TestModels/autonomous_driving.sysml")

            return semantifyrVerificationHelper.collectVerificationCases(model).asStream()
        }

        @JvmStatic
        fun `Orion Protocol Model Verification Cases Should Pass`(): Stream<Arguments> {
            val model = loadModel("TestModels/orion_protocol.sysml")

            return semantifyrVerificationHelper.collectVerificationCases(model).asStream()
        }

    }

    override val logger by loggerFactory()

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
    fun `Semantics Test Model Verification Cases Should Pass`(verificationCase: ClassDeclaration) {
        checkVerificationCase(verificationCase)
    }

    @ParameterizedTest
    @MethodSource
    fun `Crossroads Model Verification Cases Should Pass`(verificationCase: ClassDeclaration) {
        checkVerificationCase(verificationCase)
    }

    @ParameterizedTest
    @MethodSource
    fun `Compressed Spacecraft Model Verification Cases Should Pass`(verificationCase: ClassDeclaration) {
        checkVerificationCase(verificationCase)
    }

    @Tag("slow")
    @ParameterizedTest
    @MethodSource
    fun `Full Spacecraft Model Verification Cases Should Pass`(verificationCase: ClassDeclaration) {
        checkVerificationCase(verificationCase)
    }

    @ParameterizedTest
    @MethodSource
    fun `Door Access Model Verification Cases Should Pass`(verificationCase: ClassDeclaration) {
        checkVerificationCase(verificationCase)
    }

    @ParameterizedTest
    @MethodSource
    fun `Power Subsystems Model Verification Cases Should Pass`(verificationCase: ClassDeclaration) {
        checkVerificationCase(verificationCase)
    }

    @ParameterizedTest
    @MethodSource
    fun `Aircraft Engine Model Verification Cases Should Pass`(verificationCase: ClassDeclaration) {
        checkVerificationCase(verificationCase)
    }

    @ParameterizedTest
    @MethodSource
    fun `Autonomous Driving Model Verification Cases Should Pass`(verificationCase: ClassDeclaration) {
        checkVerificationCase(verificationCase)
    }

    @ParameterizedTest
    @MethodSource
    fun `Orion Protocol Model Verification Cases Should Pass`(verificationCase: ClassDeclaration) {
        checkVerificationCase(verificationCase)
    }

}
