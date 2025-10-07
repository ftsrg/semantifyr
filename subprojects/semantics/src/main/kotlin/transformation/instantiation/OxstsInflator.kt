/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.semantics.transformation.instantiation

import com.google.inject.Inject
import com.google.inject.Singleton
import hu.bme.mit.semantifyr.oxsts.lang.library.builtin.BuiltinSymbolResolver
import hu.bme.mit.semantifyr.oxsts.lang.semantics.MultiplicityRangeEvaluator
import hu.bme.mit.semantifyr.oxsts.lang.semantics.expression.BooleanEvaluation
import hu.bme.mit.semantifyr.oxsts.lang.semantics.expression.ExpressionEvaluation
import hu.bme.mit.semantifyr.oxsts.lang.semantics.expression.IntegerEvaluation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ElementReference
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Expression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.FeatureDeclaration
import hu.bme.mit.semantifyr.oxsts.model.oxsts.HavocOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlinedOxsts
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Instance
import hu.bme.mit.semantifyr.oxsts.model.oxsts.LiteralNothing
import hu.bme.mit.semantifyr.oxsts.model.oxsts.NavigationSuffixExpression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.UnaryOp
import hu.bme.mit.semantifyr.oxsts.model.oxsts.VariableDeclaration
import hu.bme.mit.semantifyr.semantics.expression.InstanceEvaluation
import hu.bme.mit.semantifyr.semantics.expression.InstanceReferenceProvider
import hu.bme.mit.semantifyr.semantics.expression.StaticExpressionEvaluatorProvider
import hu.bme.mit.semantifyr.semantics.transformation.constraints.ConstraintChecker
import hu.bme.mit.semantifyr.semantics.transformation.inliner.ExpressionRewriter
import hu.bme.mit.semantifyr.semantics.transformation.instantiation.NameHelper.INSTANCE_NAME_SEPARATOR
import hu.bme.mit.semantifyr.semantics.transformation.serializer.CompilationArtifactSaver
import hu.bme.mit.semantifyr.semantics.utils.OxstsFactory
import hu.bme.mit.semantifyr.semantics.utils.copy
import hu.bme.mit.semantifyr.semantics.utils.eAllOfType
import hu.bme.mit.semantifyr.semantics.utils.treeSequence
import org.eclipse.xtext.EcoreUtil2

@Singleton
class OxstsInflator {

    @Inject
    lateinit var oxstsClassInstantiator: OxstsClassInstantiator

    @Inject
    lateinit var builtinSymbolResolver: BuiltinSymbolResolver

    @Inject
    private lateinit var instanceManager: InstanceManager

    @Inject
    private lateinit var constraintChecker: ConstraintChecker

    @Inject
    private lateinit var staticExpressionEvaluatorProvider: StaticExpressionEvaluatorProvider

    @Inject
    private lateinit var compilationArtifactSaver: CompilationArtifactSaver

    @Inject
    private lateinit var instanceNameProvider: InstanceNameProvider

    @Inject
    private lateinit var multiplicityRangeEvaluator: MultiplicityRangeEvaluator

    @Inject
    private lateinit var expressionRewriter: ExpressionRewriter

    @Inject
    private lateinit var instanceReferenceProvider: InstanceReferenceProvider

    private val variableInstanceDomain = mutableMapOf<VariableDeclaration, Set<Instance>>()
    private var instanceId = 0
    private val instanceIds = mutableMapOf<Instance, Int>()

    fun inflateInstanceModel(inlinedOxsts: InlinedOxsts) {
        oxstsClassInstantiator.instantiateModel(inlinedOxsts)

        constraintChecker.checkConstraints(inlinedOxsts)
    }

    fun deflateInstanceModel(inlinedOxsts: InlinedOxsts) {
        variableInstanceDomain.clear()

        pullDownVariables(inlinedOxsts)
        rewriteVariableReferences(inlinedOxsts)
        rewriteFeatureTypedVariables(inlinedOxsts)

        compilationArtifactSaver.commitModelState()

        rewriteStaticExpressions(inlinedOxsts)

        compilationArtifactSaver.commitModelState()
    }

    private fun pullDownVariables(inlinedOxsts: InlinedOxsts) {
        val builtinAnything = builtinSymbolResolver.anythingClass(inlinedOxsts)
        val instances = inlinedOxsts.rootInstance.treeSequence()

        for (instance in instances) {
            val instanceReference = instanceReferenceProvider.getReference(instance)
            val evaluator = staticExpressionEvaluatorProvider.getEvaluator(instance)
            val instanceName = instanceNameProvider.getInstanceName(instance)
            val variables = instanceManager.actualVariables(instance)

            for (variable in variables) {
                if (variable.type is FeatureDeclaration) {
                    val instances = evaluator.evaluateInstances(OxstsFactory.createElementReference(variable.type))
                    variableInstanceDomain[variable] = instances
                    variable.type = builtinAnything
                }
                variable.name = "$instanceName$INSTANCE_NAME_SEPARATOR${variable.name}"

                expressionRewriter.rewriteExpressionsToContext(variable, instanceReference.copy())
            }

            inlinedOxsts.variables += variables
        }
    }

    private fun rewriteVariableReferences(inlinedOxsts: InlinedOxsts) {
        val evaluator = staticExpressionEvaluatorProvider.getEvaluator(inlinedOxsts.rootInstance)

        // not inlined variable references must be navigation references in the inlined context!
        val variableReferences = inlinedOxsts.eAllOfType<NavigationSuffixExpression>().filter {
            it.member is VariableDeclaration
        }.toList()

        for (variableReference in variableReferences) {
            val originalVariable = variableReference.member as VariableDeclaration
            val containerInstance = evaluator.evaluateSingleInstance(variableReference.primary)
            val instanceVariable = instanceManager.resolveVariable(containerInstance, originalVariable)
            val instanceVariableReference = OxstsFactory.createElementReference(instanceVariable)
            EcoreUtil2.replace(variableReference, instanceVariableReference)
        }
    }

    private fun rewriteFeatureTypedVariables(inlinedOxsts: InlinedOxsts) {
        val featureTypedVariables = variableInstanceDomain.keys

        for (variable in featureTypedVariables) {
            val references = inlinedOxsts.eAllOfType<ElementReference>().filter {
                it.element == variable
            }.toList()

            for (reference in references) {
                transformFeatureTypedVariableReference(reference)
            }
        }
    }

    private fun transformFeatureTypedVariableReference(elementReference: ElementReference) {
        val container = elementReference.eContainer()

        when (container) {
            is HavocOperation -> transformFeatureTypedVariableReference(elementReference, container)
        }
    }

    private fun transformFeatureTypedVariableReference(elementReference: ElementReference, container: HavocOperation) {
        val variable = elementReference.element as VariableDeclaration
        val instances = variableInstanceDomain[variable]!!
        val havocChoice = OxstsFactory.createChoiceOperation()

        val multiplicityRange = multiplicityRangeEvaluator.evaluate(variable)

        if (multiplicityRange.lowerBound <= 0) {
            havocChoice.branches += OxstsFactory.createSequenceOperation().also {
                it.steps += OxstsFactory.createAssignmentOperation().also {
                    it.reference = elementReference.copy()
                    it.expression = OxstsFactory.createLiteralNothing()
                }
            }
        }

        for (instance in instances) {
            havocChoice.branches += OxstsFactory.createSequenceOperation().also {
                it.steps += OxstsFactory.createAssignmentOperation().also {
                    it.reference = elementReference.copy()
                    it.expression = instanceReferenceProvider.getReference(instance)
                }
            }
        }

        EcoreUtil2.replace(container, havocChoice)
    }

    private fun rewriteStaticExpressions(inlinedOxsts: InlinedOxsts) {
        rewriteVariableDeclarations(inlinedOxsts)
        rewriteNothingExpressions(inlinedOxsts)
        rewriteFeatureExpressions(inlinedOxsts)

        inlinedOxsts.rootFeature = null
    }

    private fun rewriteVariableDeclarations(inlinedOxsts: InlinedOxsts) {
        val builtinInt = builtinSymbolResolver.intDatatype(inlinedOxsts)
        val featureTypedVariables = variableInstanceDomain.keys

        for (variable in featureTypedVariables) {
            variable.type = builtinInt
            variable.multiplicity = null
        }
    }

    private fun rewriteNothingExpressions(inlinedOxsts: InlinedOxsts) {
        while (true) {
            val nothingExpression = inlinedOxsts.eAllOfType<LiteralNothing>().firstOrNull()

            if (nothingExpression == null) {
                return
            }

            EcoreUtil2.replace(nothingExpression, OxstsFactory.createArithmeticUnaryOperator().also {
                it.op = UnaryOp.MINUS
                it.body = OxstsFactory.createLiteralInteger(1)
            })
        }
    }

    private fun rewriteFeatureExpressions(inlinedOxsts: InlinedOxsts) {
        val evaluator = staticExpressionEvaluatorProvider.getEvaluator(inlinedOxsts.rootInstance)

        while (true) {
            val featureExpression = inlinedOxsts.eAllOfType<NavigationSuffixExpression>().filter {
                it.member is FeatureDeclaration
            }.firstOrNull()

            if (featureExpression == null) {
                return
            }

            val evaluation = evaluator.evaluate(featureExpression)
            val expression = transformEvaluation(evaluation)

            EcoreUtil2.replace(featureExpression, expression)
        }
    }

    private fun transformEvaluation(evaluation: ExpressionEvaluation): Expression {
        return when (evaluation) {
            is BooleanEvaluation -> OxstsFactory.createLiteralBoolean(evaluation.value)
            is IntegerEvaluation -> {
                if (evaluation.value < 0) {
                    OxstsFactory.createArithmeticUnaryOperator().also {
                        it.op = UnaryOp.MINUS
                        it.body = OxstsFactory.createLiteralInteger(-evaluation.value)
                    }
                } else {
                    OxstsFactory.createLiteralInteger(evaluation.value)
                }
            }
            is InstanceEvaluation -> {
                val instance = evaluation.instances.single()
                val id = instanceIds.getOrPut(instance) {
                    instanceId++
                }
                OxstsFactory.createLiteralInteger(id)
            }
            else -> error("Unsupported evaluation!")
        }
    }

}
