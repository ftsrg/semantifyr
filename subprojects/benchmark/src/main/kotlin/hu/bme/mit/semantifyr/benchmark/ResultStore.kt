/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.benchmark

import hu.bme.mit.semantifyr.logging.loggerFactory
import hu.bme.mit.semantifyr.logging.warn
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists
import kotlin.io.path.readLines
import kotlin.io.path.writeText

class ResultStore(
    val directory: Path,
) {
    private val logger by loggerFactory()

    val resultsFile: Path = directory.resolve("results.jsonl")

    fun load(): List<BenchmarkRecord> {
        if (!resultsFile.exists()) {
            return emptyList()
        }
        return resultsFile.readLines().filter {
            it.isNotBlank()
        }.mapNotNull {
            try {
                json.decodeFromString<BenchmarkRecord>(it)
            } catch (exception: SerializationException) {
                logger.warn(exception) { "Skipping an unreadable line of $resultsFile" }
                null
            } catch (exception: IllegalArgumentException) {
                logger.warn(exception) { "Skipping an unreadable line of $resultsFile" }
                null
            }
        }
    }

    fun replace(records: List<BenchmarkRecord>) {
        directory.createDirectories()
        val temporary = directory.resolve("results.jsonl.tmp")
        temporary.writeText(
            records.joinToString("") {
                "${json.encodeToString(it)}\n"
            },
        )
        Files.move(temporary, resultsFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }

    fun append(record: BenchmarkRecord) {
        directory.createDirectories()
        Files.writeString(
            resultsFile,
            "${json.encodeToString(record)}\n",
            StandardOpenOption.CREATE,
            StandardOpenOption.APPEND,
            StandardOpenOption.SYNC,
        )
    }

    fun clear() {
        resultsFile.deleteIfExists()
    }

    companion object {
        val json = Json {
            encodeDefaults = true
            explicitNulls = false
            ignoreUnknownKeys = true
        }
    }
}
