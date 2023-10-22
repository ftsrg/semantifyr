package hu.bme.mit.gamma.oxsts.engine.transformation

import hu.bme.mit.gamma.oxsts.engine.utils.asChainReferenceExpression
import hu.bme.mit.gamma.oxsts.engine.utils.copy
import hu.bme.mit.gamma.oxsts.engine.utils.dropLast
import hu.bme.mit.gamma.oxsts.engine.utils.isFeatureTyped
import hu.bme.mit.gamma.oxsts.model.oxsts.Enum
import hu.bme.mit.gamma.oxsts.model.oxsts.EnumLiteral
import hu.bme.mit.gamma.oxsts.model.oxsts.Expression
import hu.bme.mit.gamma.oxsts.model.oxsts.Feature
import hu.bme.mit.gamma.oxsts.model.oxsts.ReferenceExpression
import hu.bme.mit.gamma.oxsts.model.oxsts.Variable
import hu.bme.mit.gamma.oxsts.model.oxsts.VariableTypeReference

class EnumMapping(
    val enum: Enum,
    val literalMapping: Map<InstanceObject, EnumLiteral>
)

class VariableTransformer(
    private val instanceObject: InstanceObject
) {
    fun transform(variable: Variable): Variable {
        val newVariable = variable.copy()

        newVariable.name = "${instanceObject.fullyQualifiedName}__${variable.name}"

        val typing = variable.typing
        if (typing is VariableTypeReference) {
            newVariable.typing = typing.transform()
            if (variable.expression != null) {
                newVariable.expression = transformExpression(OxstsFactory.createChainReferenceExpression(variable), variable.expression as ReferenceExpression, variable)
            }
        }

        return newVariable
    }

    fun transformExpression(variableExpression: ReferenceExpression, expression: ReferenceExpression, typedVariable: Variable): Expression {
        require(typedVariable.isFeatureTyped) {
            "$typedVariable is not a feature typed variable!"
        }

        val feature = (typedVariable.typing as VariableTypeReference).reference as Feature

        val variableHolder = instanceObject.expressionEvaluator.evaluateInstanceObject(variableExpression.asChainReferenceExpression().dropLast(1))
        val enumMapping = variableHolder.featureEnumMap[feature] ?: error("There is no enum mapping for $feature in $variableHolder")

        val instanceObject = instanceObject.expressionEvaluator.evaluateInstanceObject(expression)
        val literal = enumMapping.literalMapping[instanceObject] ?: error("Referenced instance object $instanceObject is not in referenced feature $feature in $variableHolder")

        return OxstsFactory.createChainReferenceExpression(literal)
    }

    private fun VariableTypeReference.transform(): VariableTypeReference {
        if (reference !is Feature) {
            return this
        }

        return this.copy().also {
            it.reference = (reference as Feature).transform(isOptional)
        }
    }

    private fun Feature.transform(isOptional: Boolean): Enum {
        if (instanceObject.featureEnumMap.containsKey(this)) {
            return instanceObject.featureEnumMap[this]!!.enum
        }

        val enum = OxstsFactory.createEnum()
        val literalMapping = mutableMapOf<InstanceObject, EnumLiteral>()

        instanceObject.featureEnumMap[this] = EnumMapping(enum, literalMapping)

        val instances = mutableListOf<InstanceObject>()
        instances += instanceObject.featureMap[this] ?: emptyList()

        if (isOptional) {
            instances += NothingInstance
        }

        for (instance in instances) {
            val literal = OxstsFactory.createEnumLiteral(instance.enumLiteralName)
            literalMapping[instance] = literal
        }

        enum.name = "${instanceObject.fullyQualifiedName}__${this.name}__type"
        enum.literals += literalMapping.values

        return enum
    }

    private val InstanceObject.enumLiteralName: String
        get() = "${fullyQualifiedName}__literal"

}
