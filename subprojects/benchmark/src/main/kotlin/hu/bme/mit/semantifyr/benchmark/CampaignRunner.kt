/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.benchmark

import hu.bme.mit.semantifyr.logging.info
import hu.bme.mit.semantifyr.logging.loggerFactory
import hu.bme.mit.semantifyr.logging.warn
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.nio.file.Path
import java.util.UUID
import kotlin.io.path.createDirectories

interface CellAction {
    fun unavailability(cell: Cell): String?

    suspend fun run(cell: Cell, workDirectory: Path): Measurement
}

class CampaignRunner(
    private val campaign: Campaign,
    private val store: ResultStore,
    private val action: CellAction,
) {
    private val logger by loggerFactory()

    private val workRoot = store.directory.resolve("cells")

    suspend fun run(): List<BenchmarkRecord> {
        val cells = campaign.cells()
        val keys = cells.map { it.key }.toSet()
        val done = store.load().filter {
            it.campaign == campaign.name
        }.groupBy {
            it.cellKey
        }.filter { (key, records) ->
            key in keys && isDone(records)
        }
        store.replace(done.values.flatten())
        workRoot.toFile().deleteRecursively()

        val pending = cells.filter {
            it.key !in done
        }
        logger.info { "Campaign ${campaign.name}: ${cells.size} cells, ${done.size} done, ${pending.size} to run" }
        pending.forEachIndexed { index, cell ->
            logger.info { "[${index + 1}/${pending.size}] $cell" }
            runCell(cell)
        }
        return store.load()
    }

    private fun isDone(records: List<BenchmarkRecord>): Boolean {
        if (records.any { it.measurement.outcome.isRetried }) {
            return false
        }
        if (records.maxBy { it.repetition }.measurement.outcome == Outcome.TimedOut) {
            return true
        }
        return records.count { !it.warmup } >= campaign.repetitions
    }

    private suspend fun runCell(cell: Cell) {
        val reason = action.unavailability(cell)
        if (reason != null) {
            logger.info { "$cell is unavailable: $reason" }
            store.append(BenchmarkRecord(campaign.name, cell.coordinates, 1, false, Measurement(Outcome.Unavailable, message = reason)))
            return
        }
        val runs = campaign.warmups + campaign.repetitions
        for (index in 0 until runs) {
            val warmup = index < campaign.warmups
            val measurement = measure(cell)
            logger.info { "  run ${index + 1}/$runs${if (warmup) " (warmup)" else ""}: ${measurement.outcome} ${measurement.verdict ?: ""} ${measurement.wallTime ?: ""}" }
            store.append(BenchmarkRecord(campaign.name, cell.coordinates, index + 1, warmup, measurement))
            if (measurement.outcome != Outcome.Completed) {
                return
            }
        }
    }

    private suspend fun measure(cell: Cell): Measurement {
        val workDirectory = workRoot.resolve(UUID.randomUUID().toString()).createDirectories()
        try {
            return action.run(cell, workDirectory)
        } catch (exception: Throwable) {
            currentCoroutineContext().ensureActive()
            logger.warn(exception) { "$cell errored" }
            return Measurement(Outcome.Errored, message = "${exception.javaClass.simpleName}: ${exception.message.orEmpty()}")
        } finally {
            workDirectory.toFile().deleteRecursively()
        }
    }
}
