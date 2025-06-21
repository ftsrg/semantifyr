package hu.bme.mit.semantifyr.oxsts.semantifyr.utils

import hu.bme.mit.semantifyr.oxsts.model.oxsts.Feature
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Instance
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Variable

@Suppress("MemberVisibilityCanBePrivate")
object Namings {
    const val SYNTHETIC_SEPARATOR = "$"
    const val SYNTHETIC_ELEMENT_PREFIX = "$$"

    const val UNNAMED = "$$\$unnamed"

    const val LITERAL_SUFFIX = "${SYNTHETIC_ELEMENT_PREFIX}literal"
    const val TYPE_SUFFIX = "${SYNTHETIC_ELEMENT_PREFIX}type"
    const val IMPLICIT_SUFFIX = "${SYNTHETIC_ELEMENT_PREFIX}implicit"

    const val NOTHING_CONTAINMENT_NAME = "Nothing" // "${SYNTHETIC_ELEMENT_PREFIX}Nothing"
    const val NOTHING_TYPE_NAME = "NothingType" // "${SYNTHETIC_ELEMENT_PREFIX}NothingType"

    val Instance.enumLiteralName: String
        get() = "$fullyQualifiedName$LITERAL_SUFFIX"

    fun Instance.enumName(feature: Feature): String {
        return "$fullyQualifiedName$SYNTHETIC_SEPARATOR${feature.name}$TYPE_SUFFIX"
    }

    fun Instance.variableName(variable: Variable): String {
        return "$fullyQualifiedName$SYNTHETIC_SEPARATOR${variable.name}"
    }

    fun Instance.computeFullyQualifiedName(): String {
        val parentName = parent?.fullyQualifiedName ?: ""

        return "$parentName$SYNTHETIC_SEPARATOR$name"
    }

    val Feature.implicitTypeName: String
        get() = "$realName$IMPLICIT_SUFFIX"

}
