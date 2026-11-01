/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler

import hu.bme.mit.semantifyr.compiler.reader.SemantifyrLoader
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ClassDeclaration
import kotlin.io.path.Path
import kotlin.time.Duration

object SysMLCrossroads {
    const val CASE = "TrafficLightABehaviorNormalGreenUnreachableViolated"

    private val directory = Path("build", "sysml-crossroads")

    fun caseClass(loader: SemantifyrLoader): ClassDeclaration {
        val context = loader.startContext()
            .loadLibraries(directory.resolve("library"))
            .loadModel(directory.resolve("crossroads.oxsts"))
            .buildAndResolve()
        return context.modelResources.single().allContents.asSequence().filterIsInstance<ClassDeclaration>().single {
            it.name == CASE
        }
    }
}

// the first run warms the JIT, so the later runs measure the steady state
inline fun <T> warmRepetitions(block: () -> T): List<T> {
    block()
    return List(5) {
        block()
    }
}

fun List<Duration>.median(): Duration {
    return sorted()[size / 2]
}
