package hu.bme.mit.gamma.oxsts.engine.transformation

import hu.bme.mit.gamma.oxsts.model.oxsts.Enum
import hu.bme.mit.gamma.oxsts.model.oxsts.EnumLiteral
import hu.bme.mit.gamma.oxsts.model.oxsts.Feature
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
        }

        return newVariable
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
