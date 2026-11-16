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
import hu.bme.mit.semantifyr.compiler.pipeline.context.ArraySlot
import hu.bme.mit.semantifyr.compiler.pipeline.context.FlattenedCompilationContext
import hu.bme.mit.semantifyr.compiler.pipeline.context.FlatteningInfo
import hu.bme.mit.semantifyr.compiler.pipeline.context.InlinedCompilationContext
import hu.bme.mit.semantifyr.compiler.pipeline.context.InstanceIdMapping
import hu.bme.mit.semantifyr.compiler.pipeline.context.RecordField
import hu.bme.mit.semantifyr.compiler.pipeline.expression.CompileTimeExpressionEvaluatorProvider
import hu.bme.mit.semantifyr.compiler.pipeline.expression.ConstantExpressionEvaluationTransformer
import hu.bme.mit.semantifyr.compiler.pipeline.expression.InstanceEvaluation
import hu.bme.mit.semantifyr.compiler.pipeline.expression.InstanceReferenceProvider
import hu.bme.mit.semantifyr.compiler.pipeline.expression.MetaCompileTimeExpressionEvaluatorProvider
import hu.bme.mit.semantifyr.compiler.pipeline.inlining.ExpressionRewriter
import hu.bme.mit.semantifyr.compiler.pipeline.inlining.LocalVariableManager
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
import hu.bme.mit.semantifyr.oxsts.model.oxsts.LocalVarDeclarationOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.NavigationSuffixExpression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.RecordDeclaration
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
    private val recordFlatteningPass: RecordFlatteningPass,
    private val localVariableManager: LocalVariableManager,
) {

    private val logger by loggerFactory()

    fun flatten(inlinedCompilationContext: InlinedCompilationContext): FlattenedCompilationContext {
        val inlinedOxsts = inlinedCompilationContext.inlinedOxsts
        val instanceTree = inlinedCompilationContext.instanceTree

        val variableInstanceDomain = mutableMapOf<VariableDeclaration, Set<Instance>>()
        val variableHolders = mutableMapOf<VariableDeclaration, Instance>()
        val variableMappings = mutableMapOf<Instance, Map<VariableDeclaration, VariableDeclaration>>()
        val flattenedEvaluationTransformer = FlatExpressionEvaluationTransformer()

        registerRootInstanceDomains(inlinedOxsts, instanceTree, variableInstanceDomain)

        logger.debug { "Pulling down variables" }
        if (pullDownVariables(inlinedOxsts, instanceTree, variableInstanceDomain, variableHolders, variableMappings)) {
            compilationArtifactManager.commitStep(CompilationPass.VariablePullDown)
        }

        logger.debug { "Rewriting variable references (${inlinedOxsts.variables.size} variable(s))" }
        if (rewriteVariableReferences(inlinedOxsts, instanceTree, variableMappings)) {
            compilationArtifactManager.commitStep(CompilationPass.VariableReferenceRewriting)
        }

        if (registerLocalInstanceDomains(inlinedOxsts, variableInstanceDomain)) {
            compilationArtifactManager.commitStep(CompilationPass.LocalInstanceDomainRegistration)
        }

        logger.debug { "Flattening record variables" }
        val recordFieldVariables = recordFlatteningPass.flattenRecords(inlinedOxsts)
        logger.debug { "Flattened ${recordFieldVariables.size} record variable(s)" }
        if (recordFieldVariables.isNotEmpty()) {
            compilationArtifactManager.commitStep(CompilationPass.RecordFlattening)
        }
        val recordFields = registerRecordFields(recordFieldVariables, variableHolders, variableMappings)

        logger.debug { "Flattening array variables" }
        val arraySlotVariables = arrayFlatteningPass.flattenArrays(inlinedOxsts)
        logger.debug { "Flattened ${arraySlotVariables.size} array variable(s)" }
        if (arraySlotVariables.isNotEmpty()) {
            compilationArtifactManager.commitStep(CompilationPass.ArrayFlattening)
        }
        val arraySlots = registerArraySlots(arraySlotVariables, variableHolders, variableMappings, variableInstanceDomain)

        logger.debug { "Rewriting feature-typed variables" }
        if (rewriteFeatureTypedVariables(inlinedOxsts, variableInstanceDomain)) {
            compilationArtifactManager.commitStep(CompilationPass.FeatureTypedVariableRewriting)
        }

        logger.debug { "Rewriting static expressions" }
        rewriteStaticExpressions(inlinedOxsts, instanceTree, variableInstanceDomain, flattenedEvaluationTransformer)
        compilationArtifactManager.commitStep(CompilationPass.StaticExpressionRewriting)

        logger.info { "Running post-flattening optimizers" }
        val optimization = flattenedPhaseOptimizer.optimize(inlinedCompilationContext)

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
            arraySlots = arraySlots.filterKeys { it in survivingVariables },
            recordFields = recordFields.filterKeys { it in survivingVariables },
        )

        return inlinedCompilationContext.flattened(flatteningInfo, optimization)
    }

    private fun pullDownVariables(
        inlinedOxsts: InlinedOxsts,
        instanceTree: InstanceTree,
        variableInstanceDomain: MutableMap<VariableDeclaration, Set<Instance>>,
        variableHolders: MutableMap<VariableDeclaration, Instance>,
        variableMappings: MutableMap<Instance, Map<VariableDeclaration, VariableDeclaration>>,
    ): Boolean {
        val builtinAnything = builtinSymbolResolver.anythingClass(inlinedOxsts)
        val variableCount = inlinedOxsts.variables.size

        for (instance in instanceTree.rootInstance.treeSequence().drop(1)) { // drop the root instance
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
        return inlinedOxsts.variables.size > variableCount
    }

    private fun registerRootInstanceDomains(
        inlinedOxsts: InlinedOxsts,
        instanceTree: InstanceTree,
        variableInstanceDomain: MutableMap<VariableDeclaration, Set<Instance>>,
    ) {
        val builtinAnything = builtinSymbolResolver.anythingClass(inlinedOxsts)

        for (variable in inlinedOxsts.variables) {
            val domain = variable.typeSpecification?.domain as? ClassDeclaration ?: continue
            variableInstanceDomain[variable] = instanceCollector.instancesOfType(instanceTree.rootInstance, domain)
            variable.typeSpecification.domain = builtinAnything
        }
    }

    private fun registerArraySlots(
        arraySlotVariables: Map<VariableDeclaration, List<VariableDeclaration>>,
        variableHolders: MutableMap<VariableDeclaration, Instance>,
        variableMappings: Map<Instance, Map<VariableDeclaration, VariableDeclaration>>,
        variableInstanceDomain: MutableMap<VariableDeclaration, Set<Instance>>,
    ): Map<VariableDeclaration, ArraySlot> {
        val arraySlots = mutableMapOf<VariableDeclaration, ArraySlot>()
        for ((arrayVariable, slotVariables) in arraySlotVariables) {
            variableInstanceDomain.remove(arrayVariable)?.let { instances ->
                for (slotVariable in slotVariables) {
                    variableInstanceDomain[slotVariable] = instances
                }
            }
            val holder = variableHolders[arrayVariable] ?: continue
            val originalVariable = variableMappings.getValue(holder).entries.single {
                it.value == arrayVariable
            }.key
            for ((index, slotVariable) in slotVariables.withIndex()) {
                variableHolders[slotVariable] = holder
                arraySlots[slotVariable] = ArraySlot(originalVariable, index)
            }
        }
        return arraySlots
    }

    private fun registerRecordFields(
        recordFieldVariables: Map<VariableDeclaration, Map<List<VariableDeclaration>, VariableDeclaration>>,
        variableHolders: MutableMap<VariableDeclaration, Instance>,
        variableMappings: Map<Instance, Map<VariableDeclaration, VariableDeclaration>>,
    ): Map<VariableDeclaration, RecordField> {
        val recordFields = mutableMapOf<VariableDeclaration, RecordField>()
        for ((recordVariable, fieldVariables) in recordFieldVariables) {
            val holder = variableHolders[recordVariable] ?: continue
            val originalVariable = variableMappings.getValue(holder).entries.single {
                it.value == recordVariable
            }.key
            for ((path, fieldVariable) in fieldVariables) {
                variableHolders[fieldVariable] = holder
                recordFields[fieldVariable] = RecordField(originalVariable, path)
            }
        }
        return recordFields
    }

    private fun registerLocalInstanceDomains(
        inlinedOxsts: InlinedOxsts,
        variableInstanceDomain: MutableMap<VariableDeclaration, Set<Instance>>,
    ): Boolean {
        val builtinAnything = builtinSymbolResolver.anythingClass(inlinedOxsts)

        val instanceLocals = inlinedOxsts.eAllOfType<LocalVarDeclarationOperation>().filter {
            it.typeSpecification?.domain is ClassDeclaration
        }.toList()
        for (local in instanceLocals) {
            variableInstanceDomain[local] = localVariableManager.instanceDomainOf(local)
                ?: sourceError(local, "No instance domain registered for local variable '${local.name}'.")
            local.typeSpecification.domain = builtinAnything
        }
        return instanceLocals.isNotEmpty()
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
    ): Boolean {
        val evaluator = compileTimeExpressionEvaluatorProvider.getEvaluator(instanceTree.rootInstance)

        val variableReferences = inlinedOxsts.eAllOfType<NavigationSuffixExpression>().filter {
            it.member is VariableDeclaration && it.member.eContainer() !is RecordDeclaration
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
        return variableReferences.isNotEmpty()
    }

    private fun rewriteFeatureTypedVariables(
        inlinedOxsts: InlinedOxsts,
        variableInstanceDomain: Map<VariableDeclaration, Set<Instance>>,
    ): Boolean {
        val featureTypedVariables = variableInstanceDomain.keys

        val havocs = inlinedOxsts.eAllOfType<ElementReference>().filter {
            it.element in featureTypedVariables && it.eContainer() is HavocOperation
        }.toList()

        for (reference in havocs) {
            transformFeatureTypedVariableReference(reference, reference.eContainer() as HavocOperation, variableInstanceDomain)
        }
        return havocs.isNotEmpty()
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

        val featureExpressions = mutableListOf<ReferenceExpression>()
        val contents = inlinedOxsts.eAllContents()
        while (contents.hasNext()) {
            val content = contents.next()
            if (content is ReferenceExpression && metaCompileTimeExpressionEvaluatorProvider.evaluate(instanceTree.rootInstance, content) is FeatureDeclaration) {
                featureExpressions += content
                // the rewrite replaces the whole subtree
                contents.prune()
            }
        }

        for (featureExpression in featureExpressions) {
            val evaluation = evaluator.evaluate(featureExpression)
            val expression = flattenedEvaluationTransformer.transformEvaluation(evaluation)

            EcoreUtil2.replace(featureExpression, expression)
        }
        logger.debug { "rewriteFeatureExpressions rewrote ${featureExpressions.size} expression(s)" }
    }

}
