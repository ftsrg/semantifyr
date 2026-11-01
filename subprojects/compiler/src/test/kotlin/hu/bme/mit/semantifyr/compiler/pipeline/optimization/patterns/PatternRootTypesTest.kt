/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler.pipeline.optimization.patterns

import hu.bme.mit.semantifyr.compiler.pipeline.optimization.OptimizationPattern
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.Worklist
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ChoiceOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.IfOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.OxstsPackage
import org.assertj.core.api.Assertions.assertThat
import org.eclipse.emf.ecore.EClass
import org.eclipse.emf.ecore.EObject
import org.eclipse.emf.ecore.util.EcoreUtil
import org.junit.jupiter.api.Named
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.mockito.Mockito
import java.lang.reflect.Modifier
import java.nio.file.FileSystems
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.isDirectory
import kotlin.io.path.walk

class PatternRootTypesTest {

    companion object {
        private val concreteClasses = OxstsPackage.eINSTANCE.eClassifiers.filterIsInstance<EClass>().filter {
            !it.isAbstract && !it.isInterface
        }

        @JvmStatic
        fun `A Pattern Declares Every Type Its Matcher Inspects`(): List<Arguments> {
            val location = Path.of(OptimizationPattern::class.java.protectionDomain.codeSource.location.toURI())
            val classesRoot = if (location.isDirectory()) {
                location
            } else {
                FileSystems.newFileSystem(location).getPath("/")
            }
            val packagePath = OptimizationPattern::class.java.packageName.replace('.', '/')
            return classesRoot.resolve(packagePath).walk().filter {
                it.extension == "class"
            }.map {
                Class.forName(classesRoot.relativize(it).invariantSeparatorsPathString.removeSuffix(".class").replace('/', '.'), false, OptimizationPattern::class.java.classLoader)
            }.filter {
                OptimizationPattern::class.java.isAssignableFrom(it) && !it.isInterface && !Modifier.isAbstract(it.modifiers)
            }.sortedBy {
                it.name
            }.map {
                Arguments.of(Named.of(it.simpleName, it))
            }.toList()
        }
    }

    // a matcher reads nothing of an element its type guard rejects, so any call on the element, or a throw, marks the type as inspected
    private fun inspects(pattern: OptimizationPattern, eClass: EClass): Boolean {
        val element = Mockito.spy(EcoreUtil.create(eClass))
        Mockito.clearInvocations(element)
        val failed = runCatching {
            pattern.tryApply(element, Worklist())
        }.isFailure
        return failed || Mockito.mockingDetails(element).invocations.isNotEmpty()
    }

    private fun undeclaredInspectedTypes(pattern: OptimizationPattern): List<String> {
        return concreteClasses.filter { eClass ->
            inspects(pattern, eClass) && pattern.rootTypes.none {
                it.isAssignableFrom(eClass.instanceClass)
            }
        }.map {
            it.name
        }
    }

    @ParameterizedTest
    @MethodSource
    fun `A Pattern Declares Every Type Its Matcher Inspects`(patternClass: Class<*>) {
        val constructor = patternClass.constructors.single()
        val dependencies = constructor.parameterTypes.map {
            Mockito.mock(it)
        }
        val pattern = constructor.newInstance(*dependencies.toTypedArray()) as OptimizationPattern
        val inspected = concreteClasses.filter {
            inspects(pattern, it)
        }

        assertThat(inspected)
            .describedAs("the types ${patternClass.simpleName}'s matcher inspects")
            .isNotEmpty()
        assertThat(undeclaredInspectedTypes(pattern))
            .describedAs("the types ${patternClass.simpleName}'s matcher inspects outside its root types ${pattern.rootTypes.map { it.simpleName }}")
            .isEmpty()
    }

    @Test
    fun `a matcher wider than its root types is reported`() {
        val pattern = object : OptimizationPattern {
            override val rootTypes = setOf(IfOperation::class.java)

            override fun tryApply(element: EObject, worklist: Worklist<EObject>): Boolean {
                if (element !is IfOperation && element !is ChoiceOperation) {
                    return false
                }
                return element.eContainer() != null
            }
        }

        assertThat(undeclaredInspectedTypes(pattern)).containsExactly("ChoiceOperation")
    }
}
