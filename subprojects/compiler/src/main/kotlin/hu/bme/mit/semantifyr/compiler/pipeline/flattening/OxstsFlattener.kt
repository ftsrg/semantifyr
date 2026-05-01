/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler.pipeline.flattening

import com.google.inject.Inject
import hu.bme.mit.semantifyr.compiler.pipeline.artifact.CompilationArtifactManager
import hu.bme.mit.semantifyr.compiler.pipeline.artifact.CompilationPass
import hu.bme.mit.semantifyr.compiler.pipeline.artifact.DomainMappingSerializer
import hu.bme.mit.semantifyr.compiler.pipeline.context.FlattenedCompilationContext
import hu.bme.mit.semantifyr.compiler.pipeline.context.FlatteningInfo
import hu.bme.mit.semantifyr.compiler.pipeline.context.InlinedCompilationContext
import hu.bme.mit.semantifyr.compiler.pipeline.context.InstanceIdMapping
import hu.bme.mit.semantifyr.compiler.pipeline.expression.CompileTimeExpressionEvaluatorProvider
import hu.bme.mit.semantifyr.compiler.pipeline.expression.ConstantExpressionEvaluationTransformer
import hu.bme.mit.semantifyr.compiler.pipeline.expression.InstanceEvaluation
import hu.bme.mit.semantifyr.compiler.pipeline.expression.InstanceReferenceProvider
import hu.bme.mit.semantifyr.compiler.pipeline.expression.MetaCompileTimeExpressionEvaluatorProvider
import hu.bme.mit.semantifyr.compiler.pipeline.inlining.ExpressionRewriter
import hu.bme.mit.semantifyr.compiler.pipeline.instantiation.Instance
import hu.bme.mit.semantifyr.compiler.pipeline.instantiation.InstanceCollector
import hu.bme.mit.semantifyr.compiler.pipeline.instantiation.InstanceNames
import hu.bme.mit.semantifyr.compiler.pipeline.instantiation.InstanceTree
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.optimizers.FlattenedPhaseOptimizer
import hu.bme.mit.semantifyr.compiler.pipeline.utils.OxstsFactory
import hu.bme.mit.semantifyr.compiler.pipeline.utils.copy
import hu.bme.mit.semantifyr.compiler.pipeline.utils.eAllOfType
import hu.bme.mit.semantifyr.compiler.pipeline.utils.sourceError
import hu.bme.mit.semantifyr.compiler.pipeline.utils.treeSequence
import hu.bme.mit.semantifyr.logging.debug
import hu.bme.mit.semantifyr.logging.info
import hu.bme.mit.semantifyr.logging.loggerFactory
import hu.bme.mit.semantifyr.oxsts.lang.library.builtin.BuiltinSymbolResolver
import hu.bme.mit.semantifyr.oxsts.lang.scoping.domain.DomainMemberCollectionProvider
import hu.bme.mit.semantifyr.oxsts.lang.semantics.MultiplicityRangeEvaluator
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ClassDeclaration
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ElementReference
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Expression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.FeatureDeclaration
import hu.bme.mit.semantifyr.oxsts.model.oxsts.HavocOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlinedOxsts
import hu.bme.mit.semantifyr.oxsts.model.oxsts.LiteralNothing
import hu.bme.mit.semantifyr.oxsts.model.oxsts.NavigationSuffixExpression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ReferenceExpression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.UnaryOp
import hu.bme.mit.semantifyr.oxsts.model.oxsts.VariableDeclaration
import org.eclipse.xtext.EcoreUtil2

private class FlatExpressionEvaluationTransformer : ConstantExpressionEvaluationTransformer() {

    private var nextInstanceId = 0
    private val instanceToId = mutableMapOf<Instance, Int>()
    private val idToInstance = mutableMapOf<Int, Instance>()

    override fun visit(evaluation: InstanceEvaluation): Expression {
        val instance = evaluation.instances.single()
        val id = instanceToId.getOrPut(instance) {
            nextInstanceId++.also { idToInstance[it] = instance }
        }
        return OxstsFactory.createLiteralInteger(id)
    }

    fun buildMapping(): InstanceIdMapping {
        return InstanceIdMapping(instanceToId.toMap(), idToInstance.toMap())
    }

}

class OxstsFlattener @Inject constructor(
    private val builtinSymbolResolver: BuiltinSymbolResolver,
    private val domainMemberCollectionProvider: DomainMemberCollectionProvider,
    private val compileTimeExpressionEvaluatorProvider: CompileTimeExpressionEvaluatorProvider,
    private val compilationArtifactManager: CompilationArtifactManager,
    private val multiplicityRangeEvaluator: MultiplicityRangeEvaluator,
    private val expressionRewriter: ExpressionRewriter,
    private val instanceReferenceProvider: InstanceReferenceProvider,
    private val flattenedPhaseOptimizer: FlattenedPhaseOptimizer,
    private val metaCompileTimeExpressionEvaluatorProvider: MetaCompileTimeExpressionEvaluatorProvider,
    private val domainMappingSerializer: DomainMappingSerializer,
    private val instanceCollector: InstanceCollector,
    private val arrayFlatteningPass: ArrayFlatteningPass,
) {

    private val logger by loggerFactory()

    fun flatten(inlinedCompilationContext: InlinedCompilationContext): FlattenedCompilationContext {
        val inlinedOxsts = inlinedCompilationContext.inlinedOxsts
        val instanceTree = inlinedCompilationContext.instanceTree

        val variableInstanceDomain = mutableMapOf<VariableDeclaration, Set<Instance>>()
        val variableHolders = mutableMapOf<VariableDeclaration, Instance>()
        val variableMappings = mutableMapOf<Instance, Map<VariableDeclaration, VariableDeclaration>>()
        val flattenedEvaluationTransformer = FlatExpressionEvaluationTransformer()

        logger.debug { "Pulling down variables" }
        pullDownVariables(inlinedOxsts, instanceTree, variableInstanceDomain, variableHolders, variableMappings)
        logger.debug { "Rewriting variable references (${inlinedOxsts.variables.size} variable(s))" }
        rewriteVariableReferences(inlinedOxsts, instanceTree, variableMappings)

        logger.debug { "Flattening array variables" }
        val flattenedArrayCount = arrayFlatteningPass.flattenArrays(inlinedOxsts)
        logger.debug { "Flattened $flattenedArrayCount array variable(s)" }

        logger.debug { "Rewriting feature-typed variables" }
        rewriteFeatureTypedVariables(inlinedOxsts, variableInstanceDomain)

        logger.debug { "Rewriting static expressions" }
        rewriteStaticExpressions(inlinedOxsts, instanceTree, variableInstanceDomain, flattenedEvaluationTransformer)

        logger.info { "Running post-flattening optimizers" }
        flattenedPhaseOptimizer.optimize(inlinedCompilationContext)

        compilationArtifactManager.commitStep(CompilationPass.Flattening)

        val instanceIdMapping = flattenedEvaluationTransformer.buildMapping()

        domainMappingSerializer.serializeMapping(instanceIdMapping)

        val survivingVariables = inlinedOxsts.variables.toSet()
        val survivingMappings = variableMappings.mapValues { (_, inner) ->
            inner.filterValues { it in survivingVariables }
        }
        val flatteningInfo = FlatteningInfo(
            variableHolders = variableHolders.filterKeys { it in survivingVariables },
            variableInstanceDomains = variableInstanceDomain.filterKeys { it in survivingVariables },
            variableMappings = survivingMappings,
            instanceIdMapping = instanceIdMapping,
        )

        return inlinedCompilationContext.flattened(flatteningInfo)
    }

    private fun pullDownVariables(
        inlinedOxsts: InlinedOxsts,
        instanceTree: InstanceTree,
        variableInstanceDomain: MutableMap<VariableDeclaration, Set<Instance>>,
        variableHolders: MutableMap<VariableDeclaration, Instance>,
        variableMappings: MutableMap<Instance, Map<VariableDeclaration, VariableDeclaration>>,
    ) {
        val builtinAnything = builtinSymbolResolver.anythingClass(inlinedOxsts)

        for (instance in instanceTree.rootInstance.treeSequence()) {
            val memberCollection = domainMemberCollectionProvider.getMemberCollection(instance.domain)
            val domainVariables = memberCollection.declarations.filterIsInstance<VariableDeclaration>().distinct()

            val instanceMappings = mutableMapOf<VariableDeclaration, VariableDeclaration>()
            for (domainVariable in domainVariables) {
                val actualVariable = pullDownVariable(
                    domainVariable = domainVariable,
                    instance = instance,
                    instanceTree = instanceTree,
                    builtinAnything = builtinAnything,
                    variableInstanceDomain = variableInstanceDomain,
                )
                variableHolders[actualVariable] = instance
                instanceMappings[domainVariable] = actualVariable
                inlinedOxsts.variables += actualVariable
            }
            variableMappings[instance] = instanceMappings
        }
    }

    private fun pullDownVariable(
        domainVariable: VariableDeclaration,
        instance: Instance,
        instanceTree: InstanceTree,
        builtinAnything: ClassDeclaration,
        variableInstanceDomain: MutableMap<VariableDeclaration, Set<Instance>>,
    ): VariableDeclaration {
        val actualVariable = domainVariable.copy()
        val declaredDomain = actualVariable.typeSpecification.domain

        when (declaredDomain) {
            is FeatureDeclaration -> {
                val evaluator = compileTimeExpressionEvaluatorProvider.getEvaluator(instance)
                val featureInstances = evaluator.evaluateInstances(OxstsFactory.createElementReference(declaredDomain))
                variableInstanceDomain[actualVariable] = featureInstances
                actualVariable.typeSpecification.domain = builtinAnything
            }
            is ClassDeclaration -> {
                val classInstances = instanceCollector.instancesOfType(instanceTree.rootInstance, declaredDomain)
                variableInstanceDomain[actualVariable] = classInstances
                actualVariable.typeSpecification.domain = builtinAnything
            }
            else -> {
            }
        }

        actualVariable.name = "${instance.name}${InstanceNames.INSTANCE_NAME_SEPARATOR}${actualVariable.name}"
        expressionRewriter.rewriteExpressionsToContext(actualVariable, instanceReferenceProvider.getReference(instance))
        return actualVariable
    }

    private fun rewriteVariableReferences(
        inlinedOxsts: InlinedOxsts,
        instanceTree: InstanceTree,
        variableMappings: Map<Instance, Map<VariableDeclaration, VariableDeclaration>>,
    ) {
        val evaluator = compileTimeExpressionEvaluatorProvider.getEvaluator(instanceTree.rootInstance)

        val variableReferences = inlinedOxsts.eAllOfType<NavigationSuffixExpression>().filter {
            it.member is VariableDeclaration
        }.toList()

        for (variableReference in variableReferences) {
            val originalVariable = variableReference.member as VariableDeclaration
            val containerInstance = evaluator.evaluateSingleInstance(variableReference.primary)
            val instanceVariable = variableMappings[containerInstance]?.get(originalVariable)
                ?: sourceError(
                    variableReference,
                    "Variable '${originalVariable.name}' not found for instance '${containerInstance.name}'. pullDownVariables should have registered every in-scope variable on every instance before reference rewriting.",
                )
            val instanceVariableReference = OxstsFactory.createElementReference(instanceVariable)
            EcoreUtil2.replace(variableReference, instanceVariableReference)
        }
    }

    private fun rewriteFeatureTypedVariables(
        inlinedOxsts: InlinedOxsts,
        variableInstanceDomain: Map<VariableDeclaration, Set<Instance>>,
    ) {
        val featureTypedVariables = variableInstanceDomain.keys

        for (variable in featureTypedVariables) {
            val references = inlinedOxsts.eAllOfType<ElementReference>().filter {
                it.element == variable
            }.toList()

            for (reference in references) {
                transformFeatureTypedVariableReference(reference, variableInstanceDomain)
            }
        }
    }

    private fun transformFeatureTypedVariableReference(
        elementReference: ElementReference,
        variableInstanceDomain: Map<VariableDeclaration, Set<Instance>>,
    ) {
        val container = elementReference.eContainer()

        when (container) {
            is HavocOperation -> transformFeatureTypedVariableReference(elementReference, container, variableInstanceDomain)
        }
    }

    private fun transformFeatureTypedVariableReference(
        elementReference: ElementReference,
        container: HavocOperation,
        variableInstanceDomain: Map<VariableDeclaration, Set<Instance>>,
    ) {
        val variable = elementReference.element as VariableDeclaration
        val instances = checkNotNull(variableInstanceDomain[variable]) {
            "No instance domain registered for feature-typed variable '${variable.name}'."
        }
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

    private fun rewriteStaticExpressions(
        inlinedOxsts: InlinedOxsts,
        instanceTree: InstanceTree,
        variableInstanceDomain: Map<VariableDeclaration, Set<Instance>>,
        flattenedEvaluationTransformer: FlatExpressionEvaluationTransformer,
    ) {
        rewriteVariableDeclarations(inlinedOxsts, variableInstanceDomain)
        rewriteNothingExpressions(inlinedOxsts)
        rewriteFeatureExpressions(inlinedOxsts, instanceTree, flattenedEvaluationTransformer)

        inlinedOxsts.rootFeature = null
    }

    private fun rewriteVariableDeclarations(
        inlinedOxsts: InlinedOxsts,
        variableInstanceDomain: Map<VariableDeclaration, Set<Instance>>,
    ) {
        val builtinInt = builtinSymbolResolver.intDatatype(inlinedOxsts)
        val featureTypedVariables = variableInstanceDomain.keys

        for (variable in featureTypedVariables) {
            variable.typeSpecification = OxstsFactory.createTypeSpecification().also {
                it.domain = builtinInt
            }
        }
    }

    private fun rewriteNothingExpressions(inlinedOxsts: InlinedOxsts) {
        val nothingExpressions = inlinedOxsts.eAllOfType<LiteralNothing>().toList()

        for (nothingExpression in nothingExpressions) {
            val minusOne = OxstsFactory.createArithmeticUnaryOperator().also {
                it.op = UnaryOp.MINUS
                it.body = OxstsFactory.createLiteralInteger(1)
            }

            EcoreUtil2.replace(nothingExpression, minusOne)
        }
    }

    private fun rewriteFeatureExpressions(
        inlinedOxsts: InlinedOxsts,
        instanceTree: InstanceTree,
        flattenedEvaluationTransformer: FlatExpressionEvaluationTransformer,
    ) {
        val evaluator = compileTimeExpressionEvaluatorProvider.getEvaluator(instanceTree.rootInstance)

        var iteration = 0
        while (true) {
            val featureExpression = inlinedOxsts.eAllOfType<ReferenceExpression>().firstOrNull {
                metaCompileTimeExpressionEvaluatorProvider.evaluate(instanceTree.rootInstance, it) is FeatureDeclaration
            }

            if (featureExpression == null) {
                logger.debug { "rewriteFeatureExpressions finished after $iteration iteration(s)" }
                return
            }

            val evaluation = evaluator.evaluate(featureExpression)
            val expression = flattenedEvaluationTransformer.transformEvaluation(evaluation)

            EcoreUtil2.replace(featureExpression, expression)
            iteration++
        }
    }

}
