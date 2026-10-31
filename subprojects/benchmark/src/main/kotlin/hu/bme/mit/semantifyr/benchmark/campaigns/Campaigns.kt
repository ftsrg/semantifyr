/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.benchmark.campaigns

import com.google.inject.Injector
import hu.bme.mit.semantifyr.benchmark.Campaign
import hu.bme.mit.semantifyr.benchmark.CampaignBuilder
import hu.bme.mit.semantifyr.benchmark.campaign
import hu.bme.mit.semantifyr.benchmark.sources.OxstsModelSource
import hu.bme.mit.semantifyr.benchmark.sources.SysMLCorpusSource
import hu.bme.mit.semantifyr.benchmark.verification.Engine
import hu.bme.mit.semantifyr.benchmark.verification.OptimizationLevel
import hu.bme.mit.semantifyr.benchmark.verification.VerificationAxes
import hu.bme.mit.semantifyr.portfolios.Portfolios
import java.nio.file.Path
import kotlin.time.Duration.Companion.minutes

class BenchmarkSources(
    injector: Injector,
    sysmlExamples: Path,
    oxstsModels: Path,
) {
    val sysml by lazy {
        SysMLCorpusSource(injector, sysmlExamples)
    }

    val oxstsSemantic by lazy {
        OxstsModelSource(injector, oxstsModels.resolve("semantic"))
    }
}

class CampaignDefinition(
    val name: String,
    private val define: CampaignBuilder.(BenchmarkSources) -> Unit,
) {
    fun create(sources: BenchmarkSources): Campaign {
        return campaign(name) {
            define(sources)
        }
    }
}

object Campaigns {
    // theta-full races bounded configs that exhaust the heap on concurrent models such as crossroads
    private val defaultPortfolios = mapOf(
        Engine.Theta to Portfolios.ThetaCegarExpl,
        Engine.Spin to Portfolios.SpinExhaustiveDfs,
        Engine.Nuxmv to Portfolios.NuxmvIc3Invar,
        Engine.Uppaal to Portfolios.UppaalDefault,
    )

    val crossroads = CampaignDefinition("crossroads") { sources ->
        axis(VerificationAxes.model, listOf(sources.sysml.model("crossroads.sysml")) + sources.oxstsSemantic.models)
        axis(VerificationAxes.case) {
            it[VerificationAxes.model].cases
        }
        axis(VerificationAxes.engine, listOf(Engine.Theta))
        // the semantic models keep the configuration their conformance suite runs
        axis(VerificationAxes.backendConfig) {
            if (it[VerificationAxes.model] in sources.oxstsSemantic.models) {
                listOf(Portfolios.ThetaCegarExplPredCombined)
            } else {
                listOf(defaultPortfolios.getValue(it[VerificationAxes.engine]))
            }
        }
        axis(VerificationAxes.optimization, listOf(OptimizationLevel.All))
    }

    val sysmlCorpus = CampaignDefinition("sysml-corpus") { sources ->
        axis(VerificationAxes.model, sources.sysml.models)
        axis(VerificationAxes.case) {
            it[VerificationAxes.model].cases
        }
        axis(VerificationAxes.engine, listOf(Engine.Theta))
        axis(VerificationAxes.backendConfig) {
            listOf(defaultPortfolios.getValue(it[VerificationAxes.engine]))
        }
        axis(VerificationAxes.optimization, listOf(OptimizationLevel.All))
        filter {
            !it[VerificationAxes.case].nightly
        }
        timeout = 2.minutes
    }

    val spacecraft = CampaignDefinition("spacecraft") { sources ->
        axis(VerificationAxes.model, listOf(sources.sysml.model("spacecraft.sysml")))
        axis(VerificationAxes.case) {
            it[VerificationAxes.model].cases
        }
        axis(VerificationAxes.engine, listOf(Engine.Theta, Engine.Nuxmv, Engine.Uppaal))
        axis(VerificationAxes.backendConfig) {
            listOf(defaultPortfolios.getValue(it[VerificationAxes.engine]))
        }
        axis(VerificationAxes.optimization, listOf(OptimizationLevel.All))
        filter {
            it[VerificationAxes.engine] != Engine.Theta || it[VerificationAxes.case].falsification
        }
        repetitions = 1
        timeout = 10.minutes
    }

    val all = listOf(crossroads, sysmlCorpus, spacecraft)

    fun byName(name: String): CampaignDefinition {
        return all.firstOrNull {
            it.name == name
        } ?: throw IllegalArgumentException("Unknown campaign $name (known: ${all.joinToString { it.name }})")
    }
}
