/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.theta.verification.execution

import com.google.inject.Singleton
import kotlinx.coroutines.Dispatchers

@Singleton
class VerificationDispatcher {

    val limitedParallelism = 4
    val dispatcher = Dispatchers.IO.limitedParallelism(limitedParallelism)

}
