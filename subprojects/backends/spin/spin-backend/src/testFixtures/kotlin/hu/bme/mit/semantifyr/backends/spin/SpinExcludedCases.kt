/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.spin

val spinExcludedCases = mapOf(
    "semantifyr::verification::lang::arrays::ArraySlotIndependenceReach" to "unbounded integer, Spin's range analysis cannot bound it",
    "semantifyr::verification::lang::boundedness::DoublingReachesNegative" to "unbounded integer, Spin's range analysis cannot bound it; a wrapped doubling once made Spin report it Passed",
    "semantifyr::verification::lang::boundedness::DoublingStaysPositive" to "unbounded integer, Spin's range analysis cannot bound it; a wrapped doubling once made Spin report it Failed",
    "semantifyr::verification::lang::boundedness::UnboundedCounterBound" to "unbounded integer, Spin's range analysis cannot bound it",
    "semantifyr::verification::lang::boundedness::UnboundedCounterReach" to "unbounded integer, Spin's range analysis cannot bound it",
    "semantifyr::verification::lang::boundedness::UnboundedGuardAroundAssumption" to "unbounded integer, Spin's range analysis cannot bound it",
    "semantifyr::verification::lang::local_vars::LocalVarNotPersistedBound" to "unbounded integer, Spin's range analysis cannot bound it",
    "semantifyr::verification::lang::navigation::OptionalNavPresentReach" to "unbounded integer, Spin's range analysis cannot bound it",
    "semantifyr::verification::lang::records::LocalRecordLiteralReach" to "unbounded integers, Spin's range analysis cannot bound p and hits",
    "semantifyr::verification::lang::runtime_for::ExclusiveRangeBound" to "runtime for, which Spin does not translate",
    "semantifyr::verification::lang::runtime_for::ExclusiveRangeReach" to "runtime for, which Spin does not translate",
    "semantifyr::verification::lang::runtime_for::InclusiveRangeBound" to "runtime for, which Spin does not translate",
    "semantifyr::verification::lang::runtime_for::InclusiveRangeReach" to "runtime for, which Spin does not translate",
    "semantifyr::verification::lang::runtime_for::InclusiveRangeViolated" to "runtime for, which Spin does not translate",
    "semantifyr::verification::lang::runtime_for::UnreadLoopVariableBound" to "runtime for, which Spin does not translate",
    "semantifyr::verification::lang::runtime_for::UnreadLoopVariableReach" to "runtime for, which Spin does not translate",
)
