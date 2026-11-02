/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.nuxmv

val nuxmvExcludedCases = mapOf(
    "semantifyr::verification::lang::runtime_for::ExclusiveRangeBound" to "runtime for, which nuXmv does not translate",
    "semantifyr::verification::lang::runtime_for::ExclusiveRangeReach" to "runtime for, which nuXmv does not translate",
    "semantifyr::verification::lang::runtime_for::InclusiveRangeBound" to "runtime for, which nuXmv does not translate",
    "semantifyr::verification::lang::runtime_for::InclusiveRangeReach" to "runtime for, which nuXmv does not translate",
    "semantifyr::verification::lang::runtime_for::InclusiveRangeViolated" to "runtime for, which nuXmv does not translate",
    "semantifyr::verification::lang::runtime_for::UnreadLoopVariableBound" to "runtime for, which nuXmv does not translate",
    "semantifyr::verification::lang::runtime_for::UnreadLoopVariableReach" to "runtime for, which nuXmv does not translate",
)
