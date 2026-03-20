/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.theta.verification

import com.google.inject.Inject
import com.google.inject.Provider
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ClassDeclaration
import hu.bme.mit.semantifyr.semantics.BaseSemantifyrVerificationTest
import hu.bme.mit.semantifyr.semantics.InjectWithOxstsSemantics
import hu.bme.mit.semantifyr.semantics.SemantifyrVerificationHelper
import hu.bme.mit.semantifyr.semantics.StandaloneOxstsSemanticsRuntimeModule
import hu.bme.mit.semantifyr.semantics.reader.SemantifyrLoader
import hu.bme.mit.semantifyr.semantics.utils.loggerFactory
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream
import kotlin.io.path.Path
import kotlin.streams.asStream

@Tag("verification")
@InjectWithOxstsSemantics
class ThetaXstsVerificationTests : BaseSemantifyrVerificationTest<ThetaVerifier>() {

    companion object {

        private val semantifyrVerificationHelper = StandaloneOxstsSemanticsRuntimeModule.getInstance<SemantifyrVerificationHelper>()

        @JvmStatic
        fun `Simple Model Verification Cases Should Pass`(): Stream<Arguments> {
            val model = semantifyrVerificationHelper.semantifyrLoader.startContext()
                .loadModels(Path("test-models/Simple/simple.oxsts"))
                .buildAndResolve()

            return semantifyrVerificationHelper.collectNotSlowVerificationCases(model).asStream()
        }

    }

    override val logger by loggerFactory()

    @Inject
    override lateinit var oxstsVerifierProvider: Provider<ThetaVerifier>

    @ParameterizedTest
    @MethodSource
    fun `Simple Model Verification Cases Should Pass`(verificationCase: ClassDeclaration) {
        checkVerificationCase(verificationCase)
    }

}
