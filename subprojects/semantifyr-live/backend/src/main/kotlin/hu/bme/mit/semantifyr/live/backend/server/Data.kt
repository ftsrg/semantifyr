/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.live.backend.server

import hu.bme.mit.semantifyr.live.backend.Flavor
import kotlinx.serialization.Serializable

@Serializable
data class HealthResponse(
    val status: String,
    val activeSessions: Int,
    val maxSessions: Int,
)

@Serializable
data class FlavorResponse(
    val id: String,
    val displayName: String,
    val languageId: String,
    val fileName: String,
    val verify: Boolean,
    val verifyCommand: String?,
) {
    companion object {
        fun fromFlavor(flavor: Flavor) = FlavorResponse(
            id = flavor.id,
            displayName = flavor.displayName,
            languageId = flavor.languageId,
            fileName = flavor.fileName,
            verify = flavor.verifyCommand != null,
            verifyCommand = flavor.verifyCommand,
        )
    }
}

@Serializable
data class FlavorsResponse(
    val flavors: List<FlavorResponse>,
)
