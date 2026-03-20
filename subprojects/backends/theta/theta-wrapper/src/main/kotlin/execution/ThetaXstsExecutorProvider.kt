/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.theta.wrapper.execution

import com.google.inject.Inject

class ThetaXstsExecutorProvider {

    @Inject
    private lateinit var dockerBasedThetaXstsExecutor: DockerBasedThetaXstsExecutor

    @Inject
    private lateinit var shellBasedThetaXstsExecutor: ShellBasedThetaXstsExecutor

    private var resolvedExecutor: ThetaXstsExecutor? = null

    fun getExecutor(): ThetaXstsExecutor {
        if (resolvedExecutor == null) {
            resolvedExecutor = resolveExecutor()

            resolvedExecutor!!.initialize()
        }

        return resolvedExecutor!!
    }

    private fun resolveExecutor(): ThetaXstsExecutor {
        if (dockerBasedThetaXstsExecutor.check()) {
            return dockerBasedThetaXstsExecutor
        }

        if (shellBasedThetaXstsExecutor.check()) {
            return shellBasedThetaXstsExecutor
        }

        throw IllegalStateException("Could not find any working Theta Xsts executor.")
    }

}
