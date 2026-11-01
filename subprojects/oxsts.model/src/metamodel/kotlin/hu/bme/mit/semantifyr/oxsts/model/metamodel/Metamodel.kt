/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.model.metamodel

import org.eclipse.emf.codegen.ecore.genmodel.GenModelPackage
import org.eclipse.emf.common.util.Diagnostic
import org.eclipse.emf.ecore.EClass
import org.eclipse.emf.ecore.EClassifier
import org.eclipse.emf.ecore.EDataType
import org.eclipse.emf.ecore.ENamedElement
import org.eclipse.emf.ecore.EPackage
import org.eclipse.emf.ecore.EStructuralFeature
import org.eclipse.emf.ecore.ETypedElement
import org.eclipse.emf.ecore.EcoreFactory
import org.eclipse.emf.ecore.EcorePackage
import org.eclipse.emf.ecore.util.Diagnostician
import org.eclipse.emf.ecore.util.EcoreUtil
import org.eclipse.emf.ecore.xml.type.XMLTypePackage

enum class Bounds(val lower: Int, val upper: Int) {
    OPTIONAL(0, 1),
    ONE(1, 1),
    MANY(0, ETypedElement.UNBOUNDED_MULTIPLICITY),
    ONE_OR_MORE(1, ETypedElement.UNBOUNDED_MULTIPLICITY),
}

private val factory = EcoreFactory.eINSTANCE

private val dataTypes = (XMLTypePackage.eINSTANCE.eClassifiers + EcorePackage.eINSTANCE.eClassifiers).filterIsInstance<EDataType>().associateBy { it.name }

fun metamodel(
    name: String,
    nsUri: String,
    basePackage: String,
    complianceLevel: String,
    body: MetamodelBuilder.() -> Unit,
): EPackage {
    val ePackage = factory.createEPackage()
    ePackage.name = name
    ePackage.nsURI = nsUri
    ePackage.nsPrefix = name
    EcoreUtil.setAnnotation(ePackage, GenModelPackage.eNS_URI, "basePackage", basePackage)
    EcoreUtil.setAnnotation(ePackage, GenModelPackage.eNS_URI, "complianceLevel", complianceLevel)
    MetamodelBuilder(ePackage).apply(body).link()
    val errors = Diagnostician.INSTANCE.validate(ePackage).children.filter { it.severity == Diagnostic.ERROR }
    check(errors.isEmpty()) {
        errors.joinToString("\n") { "${label(it.data.first())}: ${it.message}" }
    }
    return ePackage
}

private fun label(element: Any?): String {
    return when (element) {
        is EStructuralFeature -> "${element.eContainingClass.name}.${element.name}"
        is ENamedElement -> element.name
        else -> element.toString()
    }
}

class MetamodelBuilder internal constructor(private val ePackage: EPackage) {
    private val classifiers = mutableMapOf<String, EClassifier>()
    private val links = mutableListOf<() -> Unit>()

    fun eClass(
        name: String,
        vararg supertypes: String,
        body: ClassBuilder.() -> Unit = {},
    ) {
        declareClass(name, supertypes, false, body)
    }

    fun abstractEClass(
        name: String,
        vararg supertypes: String,
        body: ClassBuilder.() -> Unit = {},
    ) {
        declareClass(name, supertypes, true, body)
    }

    fun eEnum(name: String, vararg literals: String) {
        eEnum(name, *literals.mapIndexed { index, literal -> literal to index }.toTypedArray())
    }

    fun eEnum(name: String, vararg literals: Pair<String, Int>) {
        val eEnum = factory.createEEnum()
        eEnum.name = name
        literals.forEach { (literalName, value) ->
            val literal = factory.createEEnumLiteral()
            literal.name = literalName
            literal.value = value
            eEnum.eLiterals += literal
        }
        declare(eEnum)
    }

    internal fun link() {
        links.forEach { it() }
    }

    private fun declareClass(
        name: String,
        supertypes: Array<out String>,
        abstract: Boolean,
        body: ClassBuilder.() -> Unit,
    ) {
        val eClass = factory.createEClass()
        eClass.name = name
        eClass.isAbstract = abstract
        declare(eClass)
        supertypes.forEach { supertype ->
            links += {
                eClass.eSuperTypes += resolve<EClass>(supertype, "the supertypes of $name")
            }
        }
        ClassBuilder(eClass).apply(body)
    }

    private fun declare(classifier: EClassifier) {
        check(classifiers.putIfAbsent(classifier.name, classifier) == null) { "${classifier.name} is declared twice" }
        ePackage.eClassifiers += classifier
    }

    private inline fun <reified T : EClassifier> resolve(name: String, owner: String): T {
        val classifier = classifiers[name] ?: dataTypes[name] ?: error("unknown type $name in $owner")
        return classifier as? T ?: error("$name in $owner is not an ${T::class.java.simpleName}")
    }

    inner class ClassBuilder internal constructor(private val eClass: EClass) {
        fun contains(
            name: String,
            type: String,
            bounds: Bounds = Bounds.OPTIONAL,
        ) {
            reference(name, type, bounds, true)
        }

        fun refers(
            name: String,
            type: String,
            bounds: Bounds = Bounds.OPTIONAL,
        ) {
            reference(name, type, bounds, false)
        }

        fun attribute(
            name: String,
            type: String,
            bounds: Bounds = Bounds.OPTIONAL,
            default: String? = null,
        ) {
            val attribute = factory.createEAttribute()
            attribute.defaultValueLiteral = default
            add(attribute, name, bounds)
            links += {
                attribute.eType = resolve<EDataType>(type, "${eClass.name}.$name")
            }
        }

        private fun reference(
            name: String,
            type: String,
            bounds: Bounds,
            containment: Boolean,
        ) {
            val reference = factory.createEReference()
            reference.isContainment = containment
            add(reference, name, bounds)
            links += {
                reference.eType = resolve<EClass>(type, "${eClass.name}.$name")
            }
        }

        private fun add(
            feature: EStructuralFeature,
            name: String,
            bounds: Bounds,
        ) {
            feature.name = name
            feature.lowerBound = bounds.lower
            feature.upperBound = bounds.upper
            eClass.eStructuralFeatures += feature
        }
    }
}
