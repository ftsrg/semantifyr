/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.semantics.transformation.instantiation

import com.google.inject.Inject
import hu.bme.mit.semantifyr.oxsts.lang.library.builtin.BuiltinSymbolResolver
import hu.bme.mit.semantifyr.oxsts.lang.semantics.MultiplicityRangeEvaluator
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ArithmeticUnaryOperator
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ElementReference
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Expression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.FeatureDeclaration
import hu.bme.mit.semantifyr.oxsts.model.oxsts.HavocOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlinedOxsts
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Instance
import hu.bme.mit.semantifyr.oxsts.model.oxsts.LiteralInteger
import hu.bme.mit.semantifyr.oxsts.model.oxsts.LiteralNothing
import hu.bme.mit.semantifyr.oxsts.model.oxsts.NavigationSuffixExpression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ReferenceExpression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.UnaryOp
import hu.bme.mit.semantifyr.oxsts.model.oxsts.VariableDeclaration
import hu.bme.mit.semantifyr.semantics.expression.InstanceReferenceProvider
import hu.bme.mit.semantifyr.semantics.expression.MetaStaticExpressionEvaluatorProvider
import hu.bme.mit.semantifyr.semantics.expression.DeflatedEvaluationTransformer
import hu.bme.mit.semantifyr.semantics.expression.StaticExpressionEvaluatorProvider
import hu.bme.mit.semantifyr.semantics.optimization.InlinedOxstsOperationOptimizer
import hu.bme.mit.semantifyr.semantics.transformation.constraints.ConstraintChecker
import hu.bme.mit.semantifyr.semantics.transformation.injection.scope.CompilationScoped
import hu.bme.mit.semantifyr.semantics.transformation.inliner.ExpressionRewriter
import hu.bme.mit.semantifyr.semantics.transformation.instantiation.NameHelper.INSTANCE_NAME_SEPARATOR
import hu.bme.mit.semantifyr.semantics.transformation.serializer.CompilationStateManager
import hu.bme.mit.semantifyr.semantics.utils.OxstsFactory
import hu.bme.mit.semantifyr.semantics.utils.copy
import hu.bme.mit.semantifyr.semantics.utils.eAllOfType
import hu.bme.mit.semantifyr.semantics.utils.treeSequence
import org.eclipse.xtext.EcoreUtil2

@CompilationScoped
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
    private lateinit var compilationStateManager: CompilationStateManager

    @Inject
    private lateinit var instanceNameProvider: InstanceNameProvider

    @Inject
    private lateinit var multiplicityRangeEvaluator: MultiplicityRangeEvaluator

    @Inject
    private lateinit var expressionRewriter: ExpressionRewriter

    @Inject
    private lateinit var instanceReferenceProvider: InstanceReferenceProvider

    @Inject
    private lateinit var deflatedEvaluationTransformer: DeflatedEvaluationTransformer

    @Inject
    private lateinit var inlinedOxstsOperationOptimizer: InlinedOxstsOperationOptimizer

    @Inject
    private lateinit var metaStaticExpressionEvaluatorProvider: MetaStaticExpressionEvaluatorProvider

    private val variableInstanceDomain = mutableMapOf<VariableDeclaration, Set<Instance>>()

    private val variableHolders = mutableMapOf<VariableDeclaration, Instance>()

    fun inflateInstanceModel(inlinedOxsts: InlinedOxsts) {
        oxstsClassInstantiator.instantiateModel(inlinedOxsts)

        constraintChecker.checkConstraints(inlinedOxsts)
    }

    fun deflateInstanceModel(inlinedOxsts: InlinedOxsts) {
        pullDownVariables(inlinedOxsts)
        rewriteVariableReferences(inlinedOxsts)
        rewriteFeatureTypedVariables(inlinedOxsts)

        compilationStateManager.commitModelState()

        rewriteStaticExpressions(inlinedOxsts)

        inlinedOxstsOperationOptimizer.optimize(inlinedOxsts)

        compilationStateManager.commitModelState()
    }

    private fun pullDownVariables(inlinedOxsts: InlinedOxsts) {
        val builtinAnything = builtinSymbolResolver.anythingClass(inlinedOxsts)
        val instances = inlinedOxsts.rootInstance.treeSequence()

        for (instance in instances) {
            val instanceReference = instanceReferenceProvider.getReference(instance)
            val evaluator = staticExpressionEvaluatorProvider.getEvaluator(instance)
            val instanceName = instanceNameProvider.getInstanceName(instance)
            val variables = instanceManager.actualVariables(instance)

            inlinedOxsts.variables += variables

            for (variable in variables) {
                if (variable.typeSpecification.domain is FeatureDeclaration) {
                    val instances = evaluator.evaluateInstances(OxstsFactory.createElementReference(variable.typeSpecification.domain))
                    variableInstanceDomain[variable] = instances
                    variable.typeSpecification.domain = builtinAnything
                }

                variable.name = "$instanceName$INSTANCE_NAME_SEPARATOR${variable.name}"
                variableHolders[variable] = instance

                expressionRewriter.rewriteExpressionsToContext(variable, instanceReference.copy())
            }
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

        val multiplicityRange = multiplicityRangeEvaluator.evaluate(variable.typeSpecification)

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
            variable.typeSpecification = OxstsFactory.createTypeSpecification().also {
                it.domain = builtinInt
            }
        }
    }

    private fun rewriteNothingExpressions(inlinedOxsts: InlinedOxsts) {
        while (true) {
            val nothingExpression = inlinedOxsts.eAllOfType<LiteralNothing>().firstOrNull()

            if (nothingExpression == null) {
                return
            }

            val minusOne = OxstsFactory.createArithmeticUnaryOperator().also {
                it.op = UnaryOp.MINUS
                it.body = OxstsFactory.createLiteralInteger(1)
            }

            EcoreUtil2.replace(nothingExpression, minusOne)
        }
    }

    private fun rewriteFeatureExpressions(inlinedOxsts: InlinedOxsts) {
        val evaluator = staticExpressionEvaluatorProvider.getEvaluator(inlinedOxsts.rootInstance)

        while (true) {
            val featureExpression = inlinedOxsts.eAllOfType<ReferenceExpression>().filter {
                metaStaticExpressionEvaluatorProvider.evaluate(inlinedOxsts.rootInstance, it) is FeatureDeclaration
            }.firstOrNull()

            if (featureExpression == null) {
                return
            }

            val evaluation = evaluator.evaluate(featureExpression)
            val expression = deflatedEvaluationTransformer.transformEvaluation(evaluation)

            EcoreUtil2.replace(featureExpression, expression)
        }
    }

    fun holderOfInlinedVariable(variableDeclaration: VariableDeclaration): Instance {
        return variableHolders[variableDeclaration] ?: error("Variable was not transformed!")
    }

    fun backAnnotateInstancePointers(variableDeclaration: VariableDeclaration, expression: Expression): Expression {
        if (variableInstanceDomain[variableDeclaration] == null) {
            return expression
        }

        if (expression is ArithmeticUnaryOperator && expression.op == UnaryOp.MINUS) {
            val body = expression.body
            if (body is LiteralInteger && body.value == 1) {
                return OxstsFactory.createLiteralNothing()
            }
        }

        require(expression is LiteralInteger)

        val instance = deflatedEvaluationTransformer.instanceOfId(expression.value)

        return instanceReferenceProvider.getReference(instance)
    }

}
