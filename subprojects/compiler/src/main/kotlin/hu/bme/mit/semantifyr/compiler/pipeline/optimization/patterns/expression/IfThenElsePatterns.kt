/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler.pipeline.optimization.patterns.expression

import hu.bme.mit.semantifyr.compiler.pipeline.optimization.OptimizationPattern
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.Worklist
import hu.bme.mit.semantifyr.compiler.pipeline.utils.copy
import hu.bme.mit.semantifyr.oxsts.model.oxsts.IfThenElse
import hu.bme.mit.semantifyr.oxsts.model.oxsts.LiteralBoolean
import org.eclipse.emf.ecore.EObject
import org.eclipse.emf.ecore.util.EcoreUtil
import org.eclipse.xtext.EcoreUtil2

/**
 * Folds `if true then a else b` to `a` and `if false then a else b` to `b`.
 * Without this the compiler leaves a dead branch in the IR, which can both
 * bloat the backend model and stop
 * [hu.bme.mit.semantifyr.verification.internal.SemantifyrVerifierImpl]'s
 * optimized-away short-circuit from firing when the prop body reduces to a
 * literal.
 */
class IfThenElseConstantGuardPattern : OptimizationPattern {
    override fun tryApply(element: EObject, worklist: Worklist<EObject>): Boolean {
        if (element !is IfThenElse) return false
        val guard = element.guard as? LiteralBoolean ?: return false
        val replacement = if (guard.isValue) element.then else element.`else`
        if (replacement == null) return false
        val parent = element.eContainer() ?: return false
        EcoreUtil2.replace(element, replacement)
        worklist.add(replacement)
        worklist.add(parent)
        return true
    }
}

/**
 * Folds `if g then a else a` to `a` when the two branches are structurally
 * equal. The guard becomes dead code and is dropped. Structural equality
 * here is the EMF definition (`EcoreUtil.equals`), which matches the
 * identity relation used by
 * [hu.bme.mit.semantifyr.compiler.pipeline.optimization.patterns.expression.SelfArithmeticPattern]
 * and siblings.
 */
class IfThenElseIdenticalBranchesPattern : OptimizationPattern {
    override fun tryApply(element: EObject, worklist: Worklist<EObject>): Boolean {
        if (element !is IfThenElse) return false
        val thenBranch = element.then ?: return false
        val elseBranch = element.`else` ?: return false
        if (!EcoreUtil.equals(thenBranch, elseBranch)) return false
        val parent = element.eContainer() ?: return false
        // Copy the then-branch before replacing so we don't wrench the original
        // subtree out of its parent before the replace call reads it.
        val replacement = thenBranch.copy()
        EcoreUtil2.replace(element, replacement)
        worklist.add(replacement)
        worklist.add(parent)
        return true
    }
}
