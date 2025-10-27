/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.semantics.transformation

interface ProgressContext {

    fun checkIsCancelled()

    fun reportProgress(message: String, percentage: Int)

    fun reportProgress(message: String)

}
