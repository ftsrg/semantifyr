/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.verifier.portfolio

import hu.bme.mit.semantifyr.backend.BackendConfig
import hu.bme.mit.semantifyr.backend.VerificationBackend

class PortfolioTask<T : BackendConfig>(
    backend: VerificationBackend<T>,
    config: T,
    val subPath: String,
) {
    val leg = PortfolioLeg(backend, config)
}
