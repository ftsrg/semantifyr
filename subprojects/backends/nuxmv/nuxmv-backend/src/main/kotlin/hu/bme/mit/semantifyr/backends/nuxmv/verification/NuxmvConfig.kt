/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.nuxmv.verification

import hu.bme.mit.semantifyr.backend.BackendConfig
import hu.bme.mit.semantifyr.backend.SoundnessDirection

data class NuxmvConfig(
    override val id: String,
    override val soundness: SoundnessDirection,
    val checkCommand: String,
    val setupCommand: String = "go_msat",
    val bound: Int? = null,
) : BackendConfig {
    init {
        require(bound == null || bound > 0) { "bound must be positive, got $bound" }
    }

    companion object {
        val Ic3Invar = NuxmvConfig(
            id = "ic3-invar",
            soundness = SoundnessDirection.Exact,
            checkCommand = "check_invar_ic3",
            setupCommand = "go_msat",
        )
        val BmcInvar = NuxmvConfig(
            id = "bmc-invar",
            soundness = SoundnessDirection.Exact,
            checkCommand = "msat_check_invar_bmc -a een-sorensson",
            setupCommand = "go_msat",
            bound = 30,
        )
        val BddInvar = NuxmvConfig(
            id = "bdd-invar",
            soundness = SoundnessDirection.Exact,
            checkCommand = "check_invar",
            setupCommand = "go",
        )
    }
}
