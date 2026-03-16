/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.frontends.sysml.semantics

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
class SysMLVerificationTests : BaseSemantifyrVerificationTest<ThetaVerifier>() {

    override val logger by loggerFactory()

    @Inject
    lateinit var semantifyrLoader: SemantifyrLoader

    @Inject
    override lateinit var oxstsVerifierProvider: Provider<ThetaVerifier>

    fun testSysMLModel(modelPath: Path) {
        val model = semantifyrLoader.startContext()
            .loadLibraries(Path("Library"))
            .loadModel(modelPath)
            .buildAndResolve()

        verifyVerificationCases(model)
    }

    @Test
    fun `STM21 Model Verification Cases Should Pass`() {
        testSysMLModel(Path("models/stm21.oxsts"))
    }

    @Test
    fun `STM31 Model Verification Cases Should Pass`() {
        testSysMLModel(Path("models/stm31.oxsts"))
    }

    @Test
    fun `Crossroads Model Verification Cases Should Pass`() {
        testSysMLModel(Path("models/crossroads.oxsts"))
    }

    @Test
    fun `Spacecraft Model Verification Cases Should Pass`() {
        testSysMLModel(Path("models/spacecraft.oxsts"))
    }

}
