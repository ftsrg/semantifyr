/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.theta.verification

import com.google.inject.Inject
import com.google.inject.Provider
import hu.bme.mit.semantifyr.semantics.BaseSemantifyrVerificationTest
import hu.bme.mit.semantifyr.semantics.InjectWithOxstsSemantics
import hu.bme.mit.semantifyr.semantics.reader.SemantifyrLoader
import hu.bme.mit.semantifyr.semantics.utils.loggerFactory
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import kotlin.io.path.Path

@Tag("verification")
@InjectWithOxstsSemantics
class ThetaXstsVerificationTests : BaseSemantifyrVerificationTest<ThetaVerifier>() {

    override val logger by loggerFactory()

    @Inject
    lateinit var semantifyrLoader: SemantifyrLoader

    @Inject
    override lateinit var oxstsVerifierProvider: Provider<ThetaVerifier>

    @Test
    fun `Simple Model Verification Cases Should Pass`() {
        val model = semantifyrLoader.loadStandaloneModel(Path("test-models/Simple/simple.oxsts"))

        verifyVerificationCases(model)
    }

}
