/*
 * SPDX-FileCopyrightText: 2023-2024 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.compiler.transformation.pattern

import hu.bme.mit.semantifyr.oxsts.compiler.serialization.IndentationAwareStringWriter
import hu.bme.mit.semantifyr.oxsts.compiler.serialization.indent
import hu.bme.mit.semantifyr.oxsts.compiler.utils.fullyQualifiedName
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Constraint
import hu.bme.mit.semantifyr.oxsts.model.oxsts.FeatureConstraint
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Parameter
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Pattern
import hu.bme.mit.semantifyr.oxsts.model.oxsts.PatternBody
import hu.bme.mit.semantifyr.oxsts.model.oxsts.PatternConstraint
import hu.bme.mit.semantifyr.oxsts.model.oxsts.TransitiveClosureKind
import hu.bme.mit.semantifyr.oxsts.model.oxsts.TypeConstraint

object PatternSerializer {

    private val serializedPatterns = mutableMapOf<Pattern, String>()

    val helperPatterns = """
        package oxsts_queries
        
        import "http://www.bme.hu/mit/2023/oxsts"

        pattern OXSTS___instanceType(instance: Instance, type: Type) {
            Instance.containment(instance, containment);
            find OXSTS___featureType(containment, declaredType);
            Type.supertype*(declaredType, type);
        }
        
        pattern OXSTS___featureType(feature: Feature, type: Type) {
            Feature.typing(feature, typing);
            ReferenceTyping.reference(typing, reference);
            ChainReferenceExpression.chains(reference, chaining);
            DeclarationReferenceExpression.element(chaining, type);
        }
        
        pattern OXSTS___instanceOfType(instance: Instance, typeName: java String) {
            Instance.containment(instance, containment);
            find OXSTS___featureType(containment, type);
            Type.supertype*(type, actualType);
            Type.name(actualType, typeName);
        }
        
        pattern OXSTS___instanceInFeature(container: Instance, held: Instance, typeName: java String, featureName: java String) {
            find OXSTS___instanceOfType(container, typeName);
            Instance.associations(container, association);
            Association.feature(association, feature);
            Feature.name(feature, featureName);
            Association.instances(association, held);
        }
    """.trimIndent()

    fun serialize(pattern: Pattern) = serializedPatterns.computeIfAbsent(pattern) {
        indent {
            val patternName = pattern.fullyQualifiedName
            val parameters = pattern.parameters
            val parameterString = parameters.joinToString(", ") { it.name }

            append("pattern $patternName($parameterString)")

            val bodies = pattern.patternBodies

            if (bodies.any()) {
                append(bodies.first(), parameters)

                for (body in bodies.stream().skip(1)) {
                    append(" or ")
                    append(body, parameters)
                }
            } else {
                appendLine("{")
                indent {
                    appendParameterConstraints(parameters)
                }
                append("}")
            }
        }
    }

    private fun IndentationAwareStringWriter.append(body: PatternBody, parameters: List<Parameter>) {
        appendLine("{")
        indent {
            appendParameterConstraints(parameters)
            for (constraint in body.constraints) {
                append(constraint)
            }
        }
        append("}")
    }

    private fun IndentationAwareStringWriter.appendParameterConstraints(parameters: List<Parameter>) {
        for (parameter in parameters) {
            appendLine("find OXSTS___instanceOfType(${parameter.name}, \"${parameter.type.name}\");")
        }
    }

    private fun IndentationAwareStringWriter.append(constraint: Constraint) = when (constraint) {
        is TypeConstraint -> append(constraint)
        is FeatureConstraint -> append(constraint)
        is PatternConstraint -> append(constraint)
        else -> error("Unknown type of constraint: $constraint")
    }

    private fun IndentationAwareStringWriter.append(constraint: TypeConstraint) {
        val variableName = constraint.variables.first().name
        val typeName = constraint.type.name

        if (constraint.isNegated) {
            append("neg ")
        }
        appendLine("""find OXSTS___instanceOfType($variableName, "$typeName");""")
    }

    fun IndentationAwareStringWriter.append(constraint: FeatureConstraint) {
        val holderVariableName = constraint.variables[0].name
        val heldVariableName = constraint.variables[1].name
        val typeName = constraint.type.name
        val featureName = constraint.feature.name

        if (constraint.isNegated) {
            append("neg ")
        }
        appendLine(
            """find OXSTS___instanceInFeature($holderVariableName, $heldVariableName, "$typeName", "$featureName");""",
        )
    }

    private fun IndentationAwareStringWriter.append(constraint: PatternConstraint) {
        val patternName = constraint.pattern.fullyQualifiedName
        val callModifier = when (constraint.transitiveClosure!!) {
            TransitiveClosureKind.WITHOUT_SELF -> "+"
            TransitiveClosureKind.INCLUDE_SELF -> "*"
            TransitiveClosureKind.NONE -> ""
        }

        val constraintVariables = constraint.variables.joinToString(", ") { it.name }

        if (constraint.isNegated) {
            append("neg ")
        }
        appendLine("find $patternName$callModifier($constraintVariables);")
    }

}
