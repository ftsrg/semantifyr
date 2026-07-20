/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.verifier.discovery

import hu.bme.mit.semantifyr.verifier.VerificationCase

sealed interface CaseFilter {
    fun matches(case: VerificationCase): Boolean

    object All : CaseFilter {
        override fun matches(case: VerificationCase): Boolean {
            return true
        }
    }

    /**
     * Matches when the case carries at least one tag from [including] (or [including] is empty) AND carries none of the tags from [excluding].
     */
    data class Tags(
        val including: Set<String> = emptySet(),
        val excluding: Set<String> = emptySet(),
    ) : CaseFilter {
        override fun matches(case: VerificationCase): Boolean {
            val includeOk = including.isEmpty() || case.tags.any { it in including }
            val excludeOk = case.tags.none { it in excluding }
            return includeOk && excludeOk
        }
    }

    data class Matching(val predicate: (VerificationCase) -> Boolean) : CaseFilter {
        override fun matches(case: VerificationCase): Boolean {
            return predicate(case)
        }
    }

    companion object {
        fun excluding(vararg tags: String): Tags {
            return Tags(excluding = tags.toSet())
        }

        fun tagged(vararg tags: String): Tags {
            return Tags(including = tags.toSet())
        }
    }
}
