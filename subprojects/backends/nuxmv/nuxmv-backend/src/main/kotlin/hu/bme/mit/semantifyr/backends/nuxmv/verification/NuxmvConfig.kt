/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.nuxmv.verification

import hu.bme.mit.semantifyr.backend.BackendConfig

data class NuxmvConfig(
    override val id: String,
    val checkCommand: String,
    val setupCommand: String = "go_msat",
) : BackendConfig {
    companion object {
        val Ic3Invar = NuxmvConfig(
            id = "ic3-invar",
            checkCommand = "check_invar_ic3 -p",
            setupCommand = "go_msat",
        )
        val BmcInvar = NuxmvConfig(
            id = "bmc-invar",
            checkCommand = "msat_check_invar_bmc -a een-sorensson -k 30 -p",
            setupCommand = "go_msat",
        )
        val BddInvar = NuxmvConfig(
            id = "bdd-invar",
            checkCommand = "check_invar -p",
            setupCommand = "go",
        )

        val Builtin: List<NuxmvConfig> = listOf(
            Ic3Invar,
            BmcInvar,
            BddInvar,
        )
    }
}
