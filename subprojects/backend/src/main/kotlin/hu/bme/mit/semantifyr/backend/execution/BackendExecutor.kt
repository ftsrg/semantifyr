/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backend.execution

interface BackendExecutor {

    fun isAvailable(): Boolean
    fun prepare() {
        // no-op
    }

}
