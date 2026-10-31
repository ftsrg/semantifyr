/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.nuxmv.transformation

import hu.bme.mit.semantifyr.backend.BackendTestModels
import hu.bme.mit.semantifyr.backend.scopes.withVerificationScope
import hu.bme.mit.semantifyr.backends.nuxmv.verification.NuxmvBackendModule
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlinedOxsts

abstract class NuxmvTransformationTestBase {

    companion object {
        private val models = BackendTestModels()

        private val childInjector by lazy {
            models.injector.createChildInjector(NuxmvBackendModule())
        }
    }

    protected fun capabilityChecker(): NuxmvCapabilityChecker {
        return childInjector.getInstance(NuxmvCapabilityChecker::class.java)
    }

    protected fun parse(source: String, vararg packages: String): InlinedOxsts {
        return models.parse(source, *packages)
    }

    protected suspend fun <T> withTransformer(block: suspend (NuxmvModelTransformer) -> T): T {
        return withVerificationScope {
            val transformer = childInjector.getInstance(NuxmvModelTransformer::class.java)
            block(transformer)
        }
    }
}
