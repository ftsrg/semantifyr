/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.benchmark

import com.sun.management.OperatingSystemMXBean
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import java.lang.management.ManagementFactory
import java.net.InetAddress
import kotlin.io.path.Path
import kotlin.io.path.readLines

abstract class ContextSource<V>(
    val name: String,
    val serializer: KSerializer<V>,
) {
    abstract fun record(): V

    internal fun recordAsJson(): JsonElement {
        return benchmarkJson.encodeToJsonElement(serializer, record())
    }
}

@Serializable
data class Revision(
    val commit: String?,
    val uncommittedChanges: Boolean?,
)

object CodeRevision : ContextSource<Revision>("revision", Revision.serializer()) {
    override fun record(): Revision {
        return Revision(
            commit = commandOutputOrNull("git", "rev-parse", "HEAD"),
            uncommittedChanges = commandOutputOrNull("git", "status", "--porcelain")?.isNotEmpty(),
        )
    }
}

@Serializable
data class HostDescription(
    val name: String,
    val operatingSystem: String,
    val processorModel: String?,
    val processors: Int,
    val physicalMemoryBytes: Long,
)

object Host : ContextSource<HostDescription>("host", HostDescription.serializer()) {
    override fun record(): HostDescription {
        val system = ManagementFactory.getOperatingSystemMXBean() as OperatingSystemMXBean
        val processorModel = Path("/proc/cpuinfo").readLines().firstOrNull {
            it.startsWith("model name")
        }?.substringAfter(':')?.trim()
        return HostDescription(
            name = InetAddress.getLocalHost().hostName,
            operatingSystem = "${system.name} ${system.version} ${system.arch}",
            processorModel = processorModel,
            processors = system.availableProcessors,
            physicalMemoryBytes = system.totalMemorySize,
        )
    }
}

@Serializable
data class JvmDescription(
    val name: String,
    val arguments: List<String>,
    val maximumHeapBytes: Long,
)

object Jvm : ContextSource<JvmDescription>("jvm", JvmDescription.serializer()) {
    override fun record(): JvmDescription {
        return JvmDescription(
            name = "${System.getProperty("java.vm.name")} ${System.getProperty("java.runtime.version")}",
            arguments = ManagementFactory.getRuntimeMXBean().inputArguments,
            maximumHeapBytes = Runtime.getRuntime().maxMemory(),
        )
    }
}
