/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.verifier.portfolio

import kotlin.time.Duration

class PortfolioStages(block: StagesScope.() -> Unit) {
    internal val stages = StagesScope().apply(block).stages.toList()

    val tasks = stages.flatMap { stage ->
        stage.legs.map {
            it.task
        }
    }.distinct()
}

class StagesScope internal constructor() {
    internal val stages = mutableListOf<PortfolioStage>()

    fun stage(
        name: String,
        window: (PortfolioCase) -> Duration = {
            Duration.INFINITE
        },
        skippedUnder: Duration = Duration.ZERO,
        block: StageScope.() -> Unit,
    ) {
        stages += PortfolioStage(name, window, skippedUnder, StageScope(emptyList()).apply(block).stageLegs.toList())
    }
}

class StageScope internal constructor(
    private val unlessEndedOnItsOwn: List<PortfolioTask<*>>,
) {
    internal val stageLegs = mutableListOf<StageLeg>()

    fun legs(vararg tasks: PortfolioTask<*>) {
        for (task in tasks) {
            stageLegs += StageLeg(task, unlessEndedOnItsOwn)
        }
    }

    fun unlessEndedOnItsOwn(task: PortfolioTask<*>, block: StageScope.() -> Unit) {
        stageLegs += StageScope(unlessEndedOnItsOwn + task).apply(block).stageLegs
    }
}

internal class PortfolioStage(
    val name: String,
    val window: (PortfolioCase) -> Duration,
    val skippedUnder: Duration,
    val legs: List<StageLeg>,
) {
    fun lists(task: PortfolioTask<*>): Boolean {
        return legs.any {
            it.task == task
        }
    }
}

internal class StageLeg(
    val task: PortfolioTask<*>,
    val unlessEndedOnItsOwn: List<PortfolioTask<*>>,
)
