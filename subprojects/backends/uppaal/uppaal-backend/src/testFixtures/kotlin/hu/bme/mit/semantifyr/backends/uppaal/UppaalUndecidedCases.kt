/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.uppaal

val uppaalUndecidedCases = mapOf(
    "semantifyr::verification::lang::boundedness::DoublingReachesNegative" to "Uppaal stops on the range error after 0.07 s: an assignment of 1073741824 outside the declared range (x)",
    "semantifyr::verification::lang::boundedness::DoublingStaysPositive" to "Uppaal stops on the range error after 0.05 s: an assignment of 1073741824 outside the declared range (x)",
    "semantifyr::verification::lang::boundedness::UnboundedCounterBound" to "the proof counts through Uppaal's 31-bit range at 490,000 states and 80 MB a second here, about 37 minutes and 170 GB to the range error at 2^30",
    "semantifyr::verification::lang::local_vars::LocalVariableNotPersistedBound" to "the proof counts through Uppaal's 31-bit range for 331 s and 18 GB here, then Uppaal stops on the range error (1073741832 assigned to total)",
)
