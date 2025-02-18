/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.semantifyr.transformation.pattern

import hu.bme.mit.semantifyr.oxsts.model.oxsts.Instance
import hu.bme.mit.semantifyr.oxsts.model.oxsts.SemanticConstraint
import hu.bme.mit.semantifyr.oxsts.semantifyr.utils.Namings
import hu.bme.mit.semantifyr.oxsts.semantifyr.utils.fullyQualifiedName
import hu.bme.mit.semantifyr.oxsts.semantifyr.utils.loggerFactory
import org.eclipse.viatra.query.runtime.api.GenericPatternMatch
import org.eclipse.xtext.EcoreUtil2

object ConstraintChecker {

    val logger by loggerFactory()

    fun checkConstraints(rootInstance: Instance) {
        val resourceSet = EcoreUtil2.getResourceSet(rootInstance)
        val patternRunner = PatternRunner(resourceSet)

        val constraints = resourceSet.allContents.asSequence().filterIsInstance<SemanticConstraint>()

        var matchFound = false
        for (constraint in constraints) {
            val matches = patternRunner.evaluate(constraint)

            if (matches.any()) {
                matchFound = true
                logger.error("Matches found for pattern ${constraint.name}:")

                for (match in matches) {
                    logger.error(match.toPrettyString())
                }
            }
        }

        if (matchFound) {
            error("Constraint violation!")
        }
    }

    private fun GenericPatternMatch.toPrettyString() = parameterNames().joinToString(", ") {
        val instance = get(it) as Instance
        """"$it" = ${instance.fullyQualifiedName.replace(Namings.SYNTHETIC_SEPARATOR, ".").removePrefix(".")}"""
    }

}
