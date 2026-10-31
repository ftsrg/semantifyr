/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.benchmark

import com.sun.management.OperatingSystemMXBean
import java.lang.management.ManagementFactory
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.nanoseconds

data class CpuSample(
    val process: Duration?,
    val children: Duration?,
) {
    operator fun minus(earlier: CpuSample): CpuSample {
        return CpuSample(
            process = process?.let { current -> earlier.process?.let { current - it } },
            children = children?.let { current -> earlier.children?.let { current - it } },
        )
    }

    val total: Duration?
        get() = process?.let { it + (children ?: Duration.ZERO) }
}

object CpuClock {
    private val statFile = Path.of("/proc/self/stat")

    fun sample(): CpuSample {
        return CpuSample(processCpuTime(), reapedChildrenCpuTime())
    }

    private fun processCpuTime(): Duration? {
        val bean = ManagementFactory.getOperatingSystemMXBean() as? OperatingSystemMXBean ?: return null
        val nanos = bean.processCpuTime
        if (nanos < 0) {
            return null
        }
        return nanos.nanoseconds
    }

    // the backends are child processes, whose time reaches cutime and cstime once the JVM has waited for them
    private fun reapedChildrenCpuTime(): Duration? {
        if (!statFile.exists()) {
            return null
        }
        return parseChildrenCpuTime(statFile.readText())
    }

    fun parseChildrenCpuTime(stat: String): Duration? {
        val fields = stat.substringAfterLast(')').trim().split(' ')
        val childUser = fields.getOrNull(CHILD_USER_FIELD)?.toLongOrNull() ?: return null
        val childSystem = fields.getOrNull(CHILD_SYSTEM_FIELD)?.toLongOrNull() ?: return null
        return ((childUser + childSystem) * TICK_MILLISECONDS).milliseconds
    }

    // fields 16 and 17 of proc(5), counted from field 3, the first one after the parenthesized command name
    private const val CHILD_USER_FIELD = 13
    private const val CHILD_SYSTEM_FIELD = 14

    // /proc reports in USER_HZ, which is 100 on every Linux architecture the JVM runs on
    private const val TICK_MILLISECONDS = 10L
}
