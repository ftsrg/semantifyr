/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.benchmark

import kotlinx.serialization.Serializable
import java.time.Instant
import kotlin.time.Duration

@Serializable
data class RunEnvironment(
    val campaign: String,
    val version: String?,
    val repetitions: Int,
    val warmups: Int,
    val timeout: Duration,
    val startedAt: String,
    val processors: Int,
    val operatingSystem: String,
    val java: String,
    val runexec: Boolean,
) {
    companion object {
        fun of(campaign: Campaign, runexec: Boolean): RunEnvironment {
            return RunEnvironment(
                campaign = campaign.name,
                version = RunEnvironment::class.java.`package`.implementationVersion,
                repetitions = campaign.repetitions,
                warmups = campaign.warmups,
                timeout = campaign.timeout,
                startedAt = Instant.now().toString(),
                processors = Runtime.getRuntime().availableProcessors(),
                operatingSystem = "${System.getProperty("os.name")} ${System.getProperty("os.version")} ${System.getProperty("os.arch")}",
                java = "${System.getProperty("java.vendor")} ${System.getProperty("java.version")}",
                runexec = runexec,
            )
        }
    }
}
