/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.uppaal.transformation

import hu.bme.mit.semantifyr.backend.BackendTestModels
import hu.bme.mit.semantifyr.backend.scopes.withVerificationScope
import hu.bme.mit.semantifyr.backends.uppaal.verification.UppaalBackendModule
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlinedOxsts

abstract class UppaalTransformationTestBase {

    companion object {
        private val models = BackendTestModels()

        private val childInjector by lazy {
            models.injector.createChildInjector(UppaalBackendModule())
        }
    }

    protected fun parse(source: String, vararg packages: String): InlinedOxsts {
        return models.parse(source, *packages)
    }

    protected suspend fun <T> withTransformer(block: suspend (UppaalModelTransformer) -> T): T {
        return withVerificationScope {
            val transformer = childInjector.getInstance(UppaalModelTransformer::class.java)
            block(transformer)
        }
    }
}
