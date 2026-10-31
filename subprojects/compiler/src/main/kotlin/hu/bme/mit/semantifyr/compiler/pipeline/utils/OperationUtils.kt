/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler.pipeline.utils

import hu.bme.mit.semantifyr.oxsts.model.oxsts.AbstractForOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.AssignmentOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Expression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.HavocOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlinedOxsts
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Operation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.SequenceOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.TransitionDeclaration
import org.eclipse.emf.ecore.EObject

fun Operation.writeReference(): Expression {
    return when (this) {
        is AssignmentOperation -> reference
        is HavocOperation -> reference
        else -> error("Unexpected write operation: ${this::class.simpleName}")
    }
}

fun mayWriteBetween(
    from: Operation,
    to: EObject,
    writes: Collection<Operation>,
): Boolean {
    if (writes.isEmpty()) {
        return false
    }
    val between = operationsBetween(from, to) ?: return true
    return writes.any { write ->
        generateSequence<EObject>(write) {
            it.eContainer()
        }.any {
            it in between
        }
    }
}

// null when the structure does not order from before to
private fun operationsBetween(from: Operation, to: EObject): Set<EObject>? {
    val toPath = generateSequence(to) {
        it.eContainer()
    }.toList()
    val between = mutableSetOf<EObject>()

    var fromChild: EObject = from
    var common = from.eContainer()
    while (common != null && common !in toPath) {
        between += common.runsAfter(fromChild)
        fromChild = common
        common = common.eContainer()
    }
    if (common == null || common === to) {
        return null
    }

    val commonIndex = toPath.indexOf(common)
    val toChild = toPath[commonIndex - 1]
    if (common is SequenceOperation) {
        val fromIndex = common.steps.indexOf(fromChild)
        val toIndex = common.steps.indexOf(toChild)
        if (fromIndex < 0 || fromIndex >= toIndex) {
            return null
        }
        between += common.steps.subList(fromIndex + 1, toIndex)
    } else if (common !is InlinedOxsts || fromChild !== common.initTransition || toChild !== common.mainTransition) {
        return null
    }

    for (index in 1 until commonIndex) {
        between += toPath[index].runsBefore(toPath[index - 1])
    }
    return between
}

private fun EObject.runsAfter(child: EObject): List<EObject> {
    return when {
        this is SequenceOperation -> steps.takeLastWhile { it !== child }
        this is AbstractForOperation && child === body -> listOf(body)
        this is TransitionDeclaration && isMainTransition() -> branches
        else -> emptyList()
    }
}

private fun EObject.runsBefore(child: EObject): List<EObject> {
    return when {
        this is SequenceOperation -> steps.takeWhile { it !== child }
        this is AbstractForOperation && child === body -> listOf(body)
        this is TransitionDeclaration && isMainTransition() -> branches
        else -> emptyList()
    }
}

private fun TransitionDeclaration.isMainTransition(): Boolean {
    return (eContainer() as? InlinedOxsts)?.mainTransition === this
}
