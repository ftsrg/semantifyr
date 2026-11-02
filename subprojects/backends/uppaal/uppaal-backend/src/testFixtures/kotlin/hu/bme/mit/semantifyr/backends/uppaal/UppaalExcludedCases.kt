/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.uppaal

val uppaalExcludedCases = mapOf(
    "semantifyr::verification::lang::boundedness::DoublingReachesNegative" to "a doubling leaves Uppaal's 31-bit range, which stops the run",
    "semantifyr::verification::lang::boundedness::DoublingStaysPositive" to "a doubling leaves Uppaal's 31-bit range, which stops the run",
    "semantifyr::verification::lang::boundedness::UnboundedCounterBound" to "the proof counts through Uppaal's 31-bit range, longer than the 1-minute timeout",
    "semantifyr::verification::lang::local_vars::LocalVarNotPersistedBound" to "the proof counts through Uppaal's 31-bit range, longer than the 1-minute timeout",
    "semantifyr::verification::lang::runtime_for::ExclusiveRangeBound" to "runtime for, which Uppaal does not translate",
    "semantifyr::verification::lang::runtime_for::ExclusiveRangeReach" to "runtime for, which Uppaal does not translate",
    "semantifyr::verification::lang::runtime_for::InclusiveRangeBound" to "runtime for, which Uppaal does not translate",
    "semantifyr::verification::lang::runtime_for::InclusiveRangeReach" to "runtime for, which Uppaal does not translate",
    "semantifyr::verification::lang::runtime_for::InclusiveRangeViolated" to "runtime for, which Uppaal does not translate",
    "semantifyr::verification::lang::runtime_for::UnreadLoopVariableBound" to "runtime for, which Uppaal does not translate",
    "semantifyr::verification::lang::runtime_for::UnreadLoopVariableReach" to "runtime for, which Uppaal does not translate",
)
