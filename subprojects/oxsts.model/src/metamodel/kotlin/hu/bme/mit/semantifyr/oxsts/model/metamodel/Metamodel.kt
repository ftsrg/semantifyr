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
import org.eclipse.emf.ecore.EEnum
import org.eclipse.emf.ecore.ENamedElement
import org.eclipse.emf.ecore.EPackage
import org.eclipse.emf.ecore.EStructuralFeature
import org.eclipse.emf.ecore.ETypedElement
import org.eclipse.emf.ecore.EcoreFactory
import org.eclipse.emf.ecore.EcorePackage
import org.eclipse.emf.ecore.util.Diagnostician
import org.eclipse.emf.ecore.util.EcoreUtil
import kotlin.enums.EnumEntries
import kotlin.reflect.KProperty

enum class Bounds(val lower: Int, val upper: Int) {
    OPTIONAL(0, 1),
    ONE(1, 1),
    MANY(0, ETypedElement.UNBOUNDED_MULTIPLICITY),
    ONE_OR_MORE(1, ETypedElement.UNBOUNDED_MULTIPLICITY),
}

private val factory = EcoreFactory.eINSTANCE

class MetaPackage(
    private val name: String,
    private val nsUri: String,
    private val basePackage: String,
    private val complianceLevel: String,
    private val classifiers: List<MetaClassifier>,
) {
    fun toEPackage(): EPackage {
        val duplicates = classifiers.groupBy { it }.filterValues { it.size > 1 }.keys
        check(duplicates.isEmpty()) {
            "$duplicates listed twice"
        }
        val ePackage = factory.createEPackage()
        ePackage.name = name
        ePackage.nsURI = nsUri
        ePackage.nsPrefix = name
        EcoreUtil.setAnnotation(ePackage, GenModelPackage.eNS_URI, "basePackage", basePackage)
        EcoreUtil.setAnnotation(ePackage, GenModelPackage.eNS_URI, "complianceLevel", complianceLevel)
        val eClassifiers = classifiers.associateWith { it.create() }
        ePackage.eClassifiers += eClassifiers.values
        classifiers.filterIsInstance<MetaClass>().forEach {
            it.link(eClassifiers.getValue(it) as EClass) { classifier, owner ->
                eClassifiers[classifier] ?: error("$classifier in $owner is not in the package")
            }
        }
        val errors = Diagnostician.INSTANCE.validate(ePackage).children.filter { it.severity == Diagnostic.ERROR }
        check(errors.isEmpty()) {
            errors.joinToString("\n") {
                "${label(it.data.first())}: ${it.message}"
            }
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
}

sealed class MetaClassifier {
    internal abstract fun create(): EClassifier
}

sealed interface MetaDataType<T>

abstract class EcoreDataType<T> internal constructor(internal val eDataType: EDataType) : MetaDataType<T>

object EString : EcoreDataType<String>(EcorePackage.Literals.ESTRING)

object EInt : EcoreDataType<Int>(EcorePackage.Literals.EINT)

object EBoolean : EcoreDataType<Boolean>(EcorePackage.Literals.EBOOLEAN)

object EDouble : EcoreDataType<Double>(EcorePackage.Literals.EDOUBLE)

abstract class MetaEnum<E : Enum<E>>(
    private val entries: EnumEntries<E>,
    private val value: (E) -> Int = { it.ordinal },
) : MetaClassifier(),
    MetaDataType<E> {
    override fun create(): EEnum {
        val eEnum = factory.createEEnum()
        eEnum.name = toString()
        entries.forEach {
            val literal = factory.createEEnumLiteral()
            literal.name = it.name
            literal.value = value(it)
            eEnum.eLiterals += literal
        }
        return eEnum
    }

    override fun toString(): String {
        return entries.first().declaringJavaClass.simpleName
    }
}

abstract class MetaClass(private vararg val supertypes: MetaClass) : MetaClassifier() {
    private val features = mutableMapOf<String, MetaFeature>()

    protected fun contains(bounds: Bounds = Bounds.OPTIONAL, type: () -> MetaClass): MetaFeature {
        return MetaReference(bounds, true, type)
    }

    protected fun refers(bounds: Bounds = Bounds.OPTIONAL, type: () -> MetaClass): MetaFeature {
        return MetaReference(bounds, false, type)
    }

    protected fun <T> attribute(
        type: MetaDataType<T>,
        bounds: Bounds = Bounds.OPTIONAL,
        default: T? = null,
    ): MetaFeature {
        return MetaAttribute(bounds, type, default?.toString())
    }

    internal fun declare(name: String, feature: MetaFeature) {
        features[name] = feature
    }

    override fun create(): EClass {
        val eClass = factory.createEClass()
        eClass.name = toString()
        eClass.isAbstract = this is AbstractMetaClass
        return eClass
    }

    internal fun link(eClass: EClass, resolve: (MetaClassifier, String) -> EClassifier) {
        supertypes.forEach {
            eClass.eSuperTypes += resolve(it, "the supertypes of $this") as EClass
        }
        features.forEach { (name, feature) ->
            val eFeature = when (feature) {
                is MetaReference -> {
                    val reference = factory.createEReference()
                    reference.isContainment = feature.containment
                    reference.eType = resolve(feature.type(), "$this.$name")
                    reference
                }
                is MetaAttribute -> {
                    val attribute = factory.createEAttribute()
                    attribute.eType = when (val type = feature.type) {
                        is EcoreDataType<*> -> type.eDataType
                        is MetaEnum<*> -> resolve(type, "$this.$name")
                    }
                    attribute.defaultValueLiteral = feature.default
                    attribute
                }
            }
            eFeature.name = name
            eFeature.lowerBound = feature.bounds.lower
            eFeature.upperBound = feature.bounds.upper
            eClass.eStructuralFeatures += eFeature
        }
    }

    override fun toString(): String {
        return javaClass.simpleName
    }
}

abstract class AbstractMetaClass(vararg supertypes: MetaClass) : MetaClass(*supertypes)

sealed class MetaFeature(internal val bounds: Bounds) {
    operator fun provideDelegate(owner: MetaClass, property: KProperty<*>): MetaFeature {
        owner.declare(property.name, this)
        return this
    }

    operator fun getValue(owner: MetaClass, property: KProperty<*>): MetaFeature {
        return this
    }
}

class MetaReference internal constructor(
    bounds: Bounds,
    internal val containment: Boolean,
    internal val type: () -> MetaClass,
) : MetaFeature(bounds)

class MetaAttribute internal constructor(
    bounds: Bounds,
    internal val type: MetaDataType<*>,
    internal val default: String?,
) : MetaFeature(bounds)
