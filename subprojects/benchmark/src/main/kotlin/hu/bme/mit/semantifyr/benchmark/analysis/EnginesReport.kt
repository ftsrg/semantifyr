/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.benchmark.analysis

import hu.bme.mit.semantifyr.benchmark.verification.VerificationAxes
import kotlinx.serialization.Serializable

@Serializable
data class DecidingEngines(
    val models: List<ModelEngines>,
)

@Serializable
data class ModelEngines(
    val file: String,
    val cases: List<CaseEngines>,
)

@Serializable
data class CaseEngines(
    val name: String,
    val engines: List<String>,
)

object EnginesReport {
    // a wrong verdict does not count as deciding the case, and a backend id names its configuration after a colon
    fun derive(summaries: List<CellSummary>): DecidingEngines {
        val modelAxis = VerificationAxes.model.name
        val caseAxis = VerificationAxes.case.name
        val models = summaries.filter {
            modelAxis in it.cell && caseAxis in it.cell
        }.groupBy {
            it.cell.getValue(modelAxis)
        }.map { (model, modelSummaries) ->
            val cases = modelSummaries.groupBy {
                it.cell.getValue(caseAxis)
            }.map { (case, caseSummaries) ->
                val engines = caseSummaries.filter {
                    it.asExpected != false
                }.flatMap { summary ->
                    summary.decidedBy.map {
                        it.substringBefore(':')
                    }
                }.toSortedSet()
                CaseEngines(case, engines.toList())
            }
            ModelEngines(model, cases)
        }
        return DecidingEngines(models)
    }
}
