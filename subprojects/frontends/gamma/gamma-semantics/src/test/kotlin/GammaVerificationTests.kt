/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.frontends.gamma.semantics

import com.google.inject.Inject
import com.google.inject.Provider
import hu.bme.mit.semantifyr.backends.theta.verification.ThetaVerifier
import hu.bme.mit.semantifyr.semantics.BaseSemantifyrVerificationTest
import hu.bme.mit.semantifyr.semantics.InjectWithOxstsSemantics
import hu.bme.mit.semantifyr.semantics.reader.SemantifyrLoader
import hu.bme.mit.semantifyr.semantics.utils.loggerFactory
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.io.path.Path

@Tag("verification")
@InjectWithOxstsSemantics
class GammaVerificationTests : BaseSemantifyrVerificationTest<ThetaVerifier>() {

    override val logger by loggerFactory()

    @Inject
    lateinit var semantifyrLoader: SemantifyrLoader

    @Inject
    override lateinit var oxstsVerifierProvider: Provider<ThetaVerifier>

    val transformer = StandaloneGammaTransformer()

    fun testGammaModel(gammaModelPath: Path) {
        val gammaModel = gammaModelPath.toFile()
        val oxstsModelPath = Path(gammaModel.absolutePath.replace(".gamma", ".oxsts"))
        val oxstsModel = oxstsModelPath.toFile()

        transformer.transformModel(gammaModel, oxstsModel)

        val model = semantifyrLoader.startContext()
            .loadLibraries(Path("Library"))
            .loadModel(oxstsModelPath)
            .buildAndResolve()

        verifyVerificationCases(model)
    }

    @Test
    fun `Simple Model Verification Cases Should Pass`() {
        testGammaModel(Path("TestModels/Simple.gamma"))
    }

    @Test
    fun `Crossroads Model Verification Cases Should Pass`() {
        testGammaModel(Path("TestModels/Crossroads.gamma"))
    }

    @Test
    fun `Spacecraft Model Verification Cases Should Pass`() {
        testGammaModel(Path("TestModels/Spacecraft.gamma"))
    }

}
