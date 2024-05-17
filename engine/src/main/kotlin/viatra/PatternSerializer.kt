package hu.bme.mit.gamma.oxsts.engine.viatra

import hu.bme.mit.gamma.oxsts.engine.serialization.IndentationAwareStringWriter
import hu.bme.mit.gamma.oxsts.engine.serialization.indent
import hu.bme.mit.gamma.oxsts.model.oxsts.Constraint
import hu.bme.mit.gamma.oxsts.model.oxsts.FeatureConstraint
import hu.bme.mit.gamma.oxsts.model.oxsts.Package
import hu.bme.mit.gamma.oxsts.model.oxsts.Parameter
import hu.bme.mit.gamma.oxsts.model.oxsts.Pattern
import hu.bme.mit.gamma.oxsts.model.oxsts.PatternBody
import hu.bme.mit.gamma.oxsts.model.oxsts.PatternConstraint
import hu.bme.mit.gamma.oxsts.model.oxsts.TransitiveClosureKind
import hu.bme.mit.gamma.oxsts.model.oxsts.TypeConstraint

val Pattern.fullyQualifiedName
    get() = "${(eContainer() as Package).name}__$name"

object PatternSerializer {

    val serializedPatterns = mutableMapOf<Pattern, String>()

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
            append("pattern ${pattern.fullyQualifiedName}(${pattern.parameters.map { it.name }.joinToString(", ")})")
            append(pattern.patternBodies.first(), pattern.parameters)

            for (body in pattern.patternBodies.stream().skip(1)) {
                append("or")
                append(body, pattern.parameters)
            }
        }
    }

    fun IndentationAwareStringWriter.append(body: PatternBody, parameters: List<Parameter>) {
        appendLine("{")
        indent {
            for (parameter in parameters) {
                appendLine("find OXSTS___instanceOfType(${parameter.name}, \"${parameter.type.name}\");")
            }
            for (constraint in body.constraints) {
                append(constraint)
            }
        }
        append("}")
    }

    fun IndentationAwareStringWriter.append(constraint: Constraint) = when(constraint) {
        is TypeConstraint -> append(constraint)
        is FeatureConstraint -> append(constraint)
        is PatternConstraint -> append(constraint)
        else -> error("")
    }

    fun IndentationAwareStringWriter.append(constraint: TypeConstraint) {
        if (constraint.isNegated) {
            append("neg ")
        }
        appendLine("find OXSTS___instanceOfType(${constraint.variables.first().name}, \"${constraint.type.name}\");")
    }

    fun IndentationAwareStringWriter.append(constraint: FeatureConstraint) {
        if (constraint.isNegated) {
            append("neg ")
        }
        appendLine("find OXSTS___instanceInFeature(${constraint.variables[0].name}, ${constraint.variables[1].name}, \"${constraint.type.name}\", \"${constraint.feature.name}\");")
    }

    fun IndentationAwareStringWriter.append(constraint: PatternConstraint) {
        if (constraint.isNegated) {
            append("neg ")
        }
        append("find ${constraint.pattern.fullyQualifiedName}")
        when (constraint.transitiveClosure) {
            TransitiveClosureKind.WITHOUT_SELF -> append("+")
            TransitiveClosureKind.INCLUDE_SELF -> append("*")
            TransitiveClosureKind.NONE -> { }
        }
        appendLine("(${constraint.variables.map { it.name }.joinToString(", ")});")
    }

}
