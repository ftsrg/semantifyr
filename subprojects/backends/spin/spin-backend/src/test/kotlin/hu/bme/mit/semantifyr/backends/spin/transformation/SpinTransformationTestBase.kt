/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.spin.transformation

import hu.bme.mit.semantifyr.backend.BackendTestModels
import hu.bme.mit.semantifyr.backend.scopes.withVerificationScope
import hu.bme.mit.semantifyr.backends.spin.verification.SpinBackendModule
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlinedOxsts

abstract class SpinTransformationTestBase {

    companion object {
        private val models = BackendTestModels()

        private val childInjector by lazy {
            models.injector.createChildInjector(SpinBackendModule())
        }
    }

    protected fun capabilityChecker(): SpinCapabilityChecker {
        return childInjector.getInstance(SpinCapabilityChecker::class.java)
    }

    protected fun parse(source: String, vararg packages: String): InlinedOxsts {
        return models.parse(source, *packages)
    }

    protected suspend fun <T> withTransformer(block: suspend (SpinModelTransformer) -> T): T {
        return withVerificationScope {
            val transformer = childInjector.getInstance(SpinModelTransformer::class.java)
            block(transformer)
        }
    }
}
