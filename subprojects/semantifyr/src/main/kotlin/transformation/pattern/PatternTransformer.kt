/*
 * SPDX-FileCopyrightText: 2023-2024 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.semantifyr.transformation.pattern

import hu.bme.mit.semantifyr.oxsts.model.oxsts.Pattern
import hu.bme.mit.semantifyr.oxsts.semantifyr.utils.allReferencedPatterns
import hu.bme.mit.semantifyr.oxsts.semantifyr.utils.fullyQualifiedName
import org.eclipse.viatra.query.patternlanguage.emf.util.PatternParserBuilder
import org.eclipse.viatra.query.runtime.api.GenericPatternMatcher
import org.eclipse.viatra.query.runtime.api.IQuerySpecification

object PatternTransformer {

    private val transformedPatterns = mutableMapOf<Pattern, IQuerySpecification<GenericPatternMatcher>>()

    fun transform(pattern: Pattern) = transformedPatterns.computeIfAbsent(pattern) {
        // TODO: might be too expensive, however, otherwise we cannot parse patterns again in different contexts
        // FIXME: parse all patterns at once. Note: might result in pattern conflicts
        val vqlParser = PatternParserBuilder().build()

        val referencedPatterns = pattern.allReferencedPatterns()

        val vql = buildString {
            appendLine(PatternSerializer.helperPatterns)

            for (referencedPattern in referencedPatterns) {
                appendLine()
                appendLine(PatternSerializer.serialize(referencedPattern))
            }
        }

        val results = vqlParser.parse(vql)

        if (results.hasError()) {
            error("Errors in VQL of ${pattern.fullyQualifiedName}:\n" + vql + "\n" + results.errors)
        }

        @Suppress("UNCHECKED_CAST") // reflective VQL parser always returns GenericPatternMatchers
        results.querySpecifications.first {
            it.simpleName == pattern.fullyQualifiedName
        } as IQuerySpecification<GenericPatternMatcher>
    }

}
