/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.theta.wrapper.execution

sealed interface ThetaExecutorSpec {

    object Auto : ThetaExecutorSpec

    object Shell : ThetaExecutorSpec

    data class Docker(
        val image: String = DEFAULT_IMAGE,
    ) : ThetaExecutorSpec {
        companion object {
            const val DEFAULT_IMAGE = "ftsrg/theta-xsts-cli:latest"
        }
    }
}
