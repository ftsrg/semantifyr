/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.frontends.gamma.serialization

import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.AG
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.Action
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.ArithmeticBinaryOperator
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.ArithmeticOp
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.ArithmeticUnaryOperator
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.AssignmentAction
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.AsynchronousAdapterDeclaration
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.AsynchronousCompositeComponentDeclaration
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.Block
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.BooleanOp
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.BooleanOperator
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.BooleanType
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.CascadeComponentDeclaration
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.Channel
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.ComparisonOp
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.ComparisonOperator
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.ComponentDeclaration
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.ComponentInstance
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.CompositeComponentDeclaration
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.DirectReferenceExpression
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.EF
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.EventDeclaration
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.EventDirection
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.EventParameterReferenceExpression
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.EventTrigger
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.Expression
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.FieldDeclaration
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.GammaModelPackage
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.GammaUtils
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.GuardEvaluation
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.InitialState
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.InstanceElementExpression
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.InstancePortReference
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.IntegerType
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.InterfaceDeclaration
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.LiteralBoolean
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.LiteralExpression
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.LiteralInteger
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.NamedElement
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.NegationOperator
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.ParameterDeclaration
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.Persistency
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.PortBinding
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.PortDeclaration
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.RaiseEventAct
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.RaiseEventAction
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.RealizationMode
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.RecordAccessExpression
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.RecordLiteralExpression
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.RecordType
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.Region
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.ScheduledAsynchronousCompositeComponentDeclaration
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.SchedulingOrder
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.SetTimeoutAction
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.ShallowHistoryState
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.State
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.StateNode
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.StateReferenceExpression
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.StatechartDeclaration
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.SyncComponentDeclaration
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.TemporalOperator
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.TimeUnit
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.TimeoutDeclaration
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.TimeoutTrigger
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.Transition
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.TransitionPriority
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.Trigger
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.Type
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.TypeDeclaration
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.UnaryOp
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.VariableDeclaration
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.VariableReferenceExpression
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.VerificationCaseDeclaration
import hu.bme.mit.semantifyr.utils.text.IndentingStringBuilder
import hu.bme.mit.semantifyr.utils.text.buildIndented
import org.eclipse.emf.ecore.EObject
import org.eclipse.xtext.EcoreUtil2
import org.eclipse.xtext.nodemodel.util.NodeModelUtils

class GammaToOxstsSerializer(
    private val nameMap: GammaNameMap,
) {

    fun transformToOxsts(gammaPackage: GammaModelPackage) = buildIndented {
        UnmappedGammaConstructs.check(gammaPackage)
        appendLine("package ${markQualifiedName(gammaPackage.name)}")
        appendLine()
        appendLine("import semantifyr::gamma::expressions")
        appendLine("import semantifyr::gamma::variables")
        appendLine("import semantifyr::gamma::statecharts")
        appendLine("import semantifyr::gamma::components")
        appendLine("import semantifyr::gamma::triggers")
        appendLine("import semantifyr::gamma::actions")
        appendLine("import semantifyr::gamma::events")
        appendLine("import semantifyr::gamma::ports")
        appendLine("import semantifyr::gamma::verification")
        appendLine("import semantifyr::gamma::asynchronous")
        for (importStatement in gammaPackage.imports) {
            appendLine("import ${markQualifiedName(importStatement.importedPackage.name)}")
        }
        appendLine()

        for (typeDeclaration in gammaPackage.typeDeclarations) {
            if (typeDeclaration.type is RecordType) {
                serializeRecord(typeDeclaration)
            }
        }

        for (interfaceDeclaration in gammaPackage.interfaces) {
            serialize(interfaceDeclaration)
        }

        for (component in gammaPackage.components) {
            serialize(component)
        }

        for (verificationCase in gammaPackage.verificationCases) {
            serialize(verificationCase)
        }
    }

    private fun IndentingStringBuilder.serializeRecord(typeDeclaration: TypeDeclaration) {
        val recordType = typeDeclaration.type as RecordType
        val recordName = nameMap.declare(typeDeclaration.eContainer(), typeDeclaration, markName(typeDeclaration.name))
        val qualifiedRecord = qualifiedClassName(typeDeclaration)

        appendIndent("record $recordName") {
            for (field in recordType.fields) {
                val fieldName = nameMap.declare(typeDeclaration, field, markName(field.name))
                appendLine("var $fieldName: ${oxstsType(field.type)} := ${defaultValue(field.type)}")
            }
        }

        appendIndent("class $PARAMETER_CLASS$recordName : Parameter") {
            appendLine("var value: $qualifiedRecord := ${defaultValue(recordType)}")
            appendLine()
            appendIndent("redefine prop evaluate(): $qualifiedRecord") {
                appendLine("return value")
            }
            appendLine()
            appendIndent("redefine tran set(value: $qualifiedRecord)") {
                appendLine("self.value := value")
            }
            appendLine()
            appendIndent("redefine tran reset()") {
                appendLine("value := ${defaultValue(recordType)}")
            }
            appendLine()
            appendIndent("redefine tran havoc()") {
                appendLine("havoc(value)")
            }
        }

        appendIndent("class $VARIABLE_CLASS$recordName : Variable") {
            val defaults = defaultBindings(recordType)
            for (binding in defaults) {
                appendLine("refers ${binding.name}: ${oxstsType(binding.path.last().type)} = ${defaultValue(binding.path.last().type)}")
            }
            appendLine()
            appendLine("var variable: $qualifiedRecord := ${initializer(recordType, emptyList(), defaults)}")
            appendLine()
            appendIndent("redefine prop evaluate(): $qualifiedRecord") {
                appendLine("return variable")
            }
            appendLine()
            appendIndent("redefine tran set(value: $qualifiedRecord)") {
                appendLine("variable := value")
            }
        }

        for (field in recordType.fields) {
            appendIndent("class ${accessClassNames(gammaPackageOf(typeDeclaration)).getValue(field)} : FieldAccessExpression") {
                appendIndent("redefine prop evaluate(): any") {
                    appendLine("return (operand.evaluate() as $qualifiedRecord).${markName(field.name)}")
                }
                appendLine()
                appendIndent("redefine prop updated(original: any, value: any): any") {
                    val fields = recordType.fields.joinToString(", ") {
                        if (it == field) {
                            "${markName(it.name)} = value as ${oxstsType(it.type)}"
                        } else {
                            "${markName(it.name)} = (original as $qualifiedRecord).${markName(it.name)}"
                        }
                    }
                    appendLine("return $qualifiedRecord { $fields }")
                }
            }
        }
    }

    private class DefaultBinding(
        val path: List<FieldDeclaration>,
        val name: String,
    )

    // one binding per leaf field, since a feature binding cannot hold a record
    private fun defaultBindings(recordType: RecordType): List<DefaultBinding> {
        val bindings = mutableListOf<DefaultBinding>()
        val taken = mutableSetOf<String>()
        fun collect(type: RecordType, prefix: List<FieldDeclaration>) {
            for (field in type.fields) {
                val path = prefix + field
                val fieldRecord = recordTypeOf(field.type)
                if (fieldRecord != null) {
                    collect(fieldRecord, path)
                    continue
                }
                val derived = "default" + path.joinToString("") {
                    markName(it.name)
                }
                var name = derived
                var number = 2
                while (!taken.add(name)) {
                    name = "${derived}_$number"
                    number++
                }
                bindings += DefaultBinding(path, name)
            }
        }
        collect(recordType, emptyList())
        return bindings
    }

    private fun initializer(
        recordType: RecordType,
        prefix: List<FieldDeclaration>,
        defaults: List<DefaultBinding>,
    ): String {
        val fields = recordType.fields.joinToString(", ") { field ->
            val path = prefix + field
            val fieldRecord = recordTypeOf(field.type)
            val value = if (fieldRecord != null) {
                initializer(fieldRecord, path, defaults)
            } else {
                defaults.single {
                    it.path == path
                }.name
            }
            "${markName(field.name)} = $value"
        }
        return "${qualifiedClassName(recordType.eContainer() as TypeDeclaration)} { $fields }"
    }

    private fun recordTypeOf(type: Type): RecordType? {
        return GammaUtils.resolveType(type) as? RecordType
    }

    private fun recordDeclarationOf(type: Type): TypeDeclaration? {
        return recordTypeOf(type)?.eContainer() as? TypeDeclaration
    }

    private fun recordClassName(prefix: String, typeDeclaration: TypeDeclaration): String {
        return "${markQualifiedName(gammaPackageOf(typeDeclaration).name)}::$prefix${markName(typeDeclaration.name)}"
    }

    private fun accessClassName(field: FieldDeclaration): String {
        val gammaPackage = gammaPackageOf(field)
        return "${markQualifiedName(gammaPackage.name)}::${accessClassNames(gammaPackage).getValue(field)}"
    }

    // the names of a package's access classes depend on that package alone, so every package that reads a field names its class alike
    private fun accessClassNames(gammaPackage: GammaModelPackage): Map<FieldDeclaration, String> {
        return accessClassNamesByPackage.getOrPut(gammaPackage) {
            val taken = mutableSetOf<String>()
            val packageNames = mutableMapOf<FieldDeclaration, String>()
            for (declaration in gammaPackage.typeDeclarations) {
                val recordType = declaration.type as? RecordType ?: continue
                for (recordField in recordType.fields) {
                    val derived = "$ACCESS_CLASS${markName(declaration.name)}${markName(recordField.name)}"
                    var name = derived
                    var number = 2
                    while (!taken.add(name)) {
                        name = "${derived}_$number"
                        number++
                    }
                    packageNames[recordField] = name
                }
            }
            packageNames
        }
    }

    private fun gammaPackageOf(element: EObject): GammaModelPackage {
        return EcoreUtil2.getContainerOfType(element, GammaModelPackage::class.java)
    }

    private val accessClassNamesByPackage = mutableMapOf<GammaModelPackage, Map<FieldDeclaration, String>>()

    private fun oxstsType(type: Type): String {
        return when (val resolved = GammaUtils.resolveType(type)) {
            is IntegerType -> "int"
            is BooleanType -> "bool"
            is RecordType -> qualifiedClassName(resolved.eContainer() as TypeDeclaration)
            else -> error("Unknown type: $type")
        }
    }

    private fun defaultValue(type: Type): String {
        return when (val resolved = GammaUtils.resolveType(type)) {
            is IntegerType -> "0"
            is BooleanType -> "false"
            is RecordType -> {
                val first = resolved.fields.first()
                "${qualifiedClassName(resolved.eContainer() as TypeDeclaration)} { ${markName(first.name)} = ${defaultValue(first.type)} }"
            }
            else -> error("Unknown type: $type")
        }
    }

    private fun variableClassName(type: Type): String {
        return when (val resolved = GammaUtils.resolveType(type)) {
            is IntegerType -> "IntegerVariable"
            is BooleanType -> "BooleanVariable"
            is RecordType -> recordClassName(VARIABLE_CLASS, resolved.eContainer() as TypeDeclaration)
            else -> error("Unknown type: $type")
        }
    }

    private fun parameterClassName(type: Type): String {
        return when (val resolved = GammaUtils.resolveType(type)) {
            is IntegerType -> "IntegerParameter"
            is BooleanType -> "BooleanParameter"
            is RecordType -> recordClassName(PARAMETER_CLASS, resolved.eContainer() as TypeDeclaration)
            else -> error("Unknown type: $type")
        }
    }

    private fun IndentingStringBuilder.serialize(interfaceDeclaration: InterfaceDeclaration) {
        val parents = interfaceDeclaration.parents
        if (parents.size == 1) {
            appendIndent("class ${markName(interfaceDeclaration.name)} : ${qualifiedClassName(parents.single())}") {
                for (event in GammaUtils.getAllEvents(parents.single())) {
                    declareEvent(interfaceDeclaration, event)
                }
                for (event in interfaceDeclaration.events) {
                    serialize(interfaceDeclaration, event)
                }
            }
            return
        }
        // several parents have no common superclass, so every ancestor's events are flattened in, ancestors first
        val inheritedEvents = GammaUtils.getAllEvents(interfaceDeclaration) - interfaceDeclaration.events.toSet()
        val events = inheritedEvents + interfaceDeclaration.events
        appendIndent("class ${markName(interfaceDeclaration.name)} : Interface") {
            for (event in events) {
                serialize(interfaceDeclaration, event)
            }
        }
    }

    private fun declareEvent(interfaceDeclaration: InterfaceDeclaration, event: EventDeclaration) {
        for (half in eventHalves(event)) {
            nameMap.declare(interfaceDeclaration, event, half.name)
        }
    }

    private class EventHalf(
        val name: String,
        val subsets: String,
    )

    // an inout event is an input and an output of the port, each half a feature of its own
    private fun eventHalves(event: EventDeclaration): List<EventHalf> {
        return when (event.direction) {
            EventDirection.IN -> listOf(EventHalf(markName(event.name), "inputEvents"))
            EventDirection.OUT -> listOf(EventHalf(markName(event.name), "outputEvents"))
            EventDirection.INOUT -> listOf(
                EventHalf("$INPUT_HALF${markName(event.name)}", "inputEvents"),
                EventHalf("$OUTPUT_HALF${markName(event.name)}", "outputEvents"),
            )
        }
    }

    private fun IndentingStringBuilder.serialize(interfaceDeclaration: InterfaceDeclaration, event: EventDeclaration) {
        for (half in eventHalves(event)) {
            val name = nameMap.declare(interfaceDeclaration, event, half.name)
            appendIndent("contains $name: Event subsets ${half.subsets}") {
                appendLine("@Shared")
                appendLine("redefine contains declaration: EventDeclaration")
                if (event.persistency == Persistency.PERSISTENT) {
                    appendLine("redefine refers persistent: bool = true")
                }
                for (parameter in event.parameters) {
                    serialize(event, parameter)
                }
            }
        }
    }

    private fun IndentingStringBuilder.serialize(event: EventDeclaration, parameter: ParameterDeclaration) {
        val name = nameMap.declare(event, parameter, markName(parameter.name))
        appendIndent("contains $name: ${parameterClassName(parameter.type)} subsets parameters") {
            appendLine("@Shared")
            appendLine("redefine contains declaration: ParameterDeclaration")
        }
    }

    private fun IndentingStringBuilder.serialize(component: ComponentDeclaration) = when (component) {
        is SyncComponentDeclaration -> serializeComposite(component, "SyncComponent")
        is CascadeComponentDeclaration -> serializeComposite(component, "CascadeComponent")
        is AsynchronousCompositeComponentDeclaration -> serializeAsynchronousComposite(component, "AsynchronousCompositeComponent")
        is ScheduledAsynchronousCompositeComponentDeclaration -> serializeAsynchronousComposite(component, "ScheduledAsynchronousCompositeComponent")
        is AsynchronousAdapterDeclaration -> serializeAdapter(component)
        is StatechartDeclaration -> serializeStatechartAndAdapter(component)
        else -> error("Unknown component type: $component")
    }

    private fun IndentingStringBuilder.serializeStatechartAndAdapter(statechart: StatechartDeclaration) {
        serialize(statechart)
        if (statechart.isAsynchronous) {
            serializeAdapter(statechart)
        }
    }

    // an asynchronous statechart is unfolded into Gamma's default adapter around it, so its instances are of the adapter
    private fun componentClassName(component: ComponentDeclaration): String {
        if (component is StatechartDeclaration && component.isAsynchronous) {
            return "${markQualifiedName(gammaPackageOf(component).name)}::${adapterClassName(component)}"
        }
        return qualifiedClassName(component)
    }

    private fun adapterClassName(component: ComponentDeclaration): String {
        if (component is AsynchronousAdapterDeclaration) {
            return markName(component.name)
        }
        return "$ADAPTER_CLASS${component.name}"
    }

    private fun queueFeatureName(adapter: ComponentDeclaration, queue: QueueMapping): String {
        if (queue.element is StatechartDeclaration) {
            return nameMap.declare(adapter, queue.element, "$QUEUE_CLASS${queue.name}")
        }
        return nameMap.declare(adapter, queue.element, markName(queue.name))
    }

    private fun queueClassName(adapter: ComponentDeclaration, queue: QueueMapping): String {
        val gammaPackage = gammaPackageOf(adapter)
        val name = nameMap.declareGenerated(gammaPackage, queue.element, "$QUEUE_CLASS${adapter.name}_${queue.name}")
        return "${markQualifiedName(gammaPackage.name)}::$name"
    }

    // an offered port is a reference typed Port, so its events are named on the port the adapter holds
    private fun adapterPortPath(adapter: ComponentDeclaration, port: PortDeclaration): String {
        if (adapter is AsynchronousAdapterDeclaration) {
            if (port in adapter.ports) {
                return markName(port.name)
            }
            return "${markName(adapter.wrappedComponent.name)}.${markName(port.name)}"
        }
        return "$WRAPPED_FEATURE.${markName(port.name)}"
    }

    private fun environmentalFlag(queue: QueueMapping): String {
        return "$ENVIRONMENTAL_FLAG${queue.name}"
    }

    private fun IndentingStringBuilder.serializeAdapter(component: ComponentDeclaration) {
        val mapping = AsynchronousMapping.adapterOf(component)
        val wrappedFeature = if (component is AsynchronousAdapterDeclaration) {
            val wrapped = component.wrappedComponent
            nameMap.declareTyped(component, wrapped, markName(wrapped.name), wrapped.component)
        } else {
            nameMap.declare(component, component, WRAPPED_FEATURE)
        }
        appendIndent("class ${adapterClassName(component)} : AsynchronousAdapter") {
            if (component is AsynchronousAdapterDeclaration) {
                appendLine("contains $wrappedFeature: ${componentClassName(mapping.wrappedComponent)} redefines wrapped")
                for (port in component.ports) {
                    serialize(port)
                }
            } else {
                appendLine("redefine contains wrapped: ${qualifiedClassName(component)}")
            }

            // the adapter offers the wrapped component's ports as its own, as in Gamma
            for (port in mapping.wrappedComponent.ports) {
                val name = nameMap.declare(component, port, markName(port.name))
                appendLine("refers $name: Port subsets ports = $wrappedFeature.$name")
            }

            for (clock in mapping.clocks) {
                val name = nameMap.declare(component, clock.clock, markName(clock.clock.name))
                appendIndent("contains $name: Clock subsets clocks") {
                    appendLine("redefine refers rate: int = ${clock.rate}")
                    if (clock.deltaTime != 1) {
                        appendLine("redefine refers deltaTime: int = ${clock.deltaTime}")
                    }
                }
            }

            for ((position, queue) in mapping.queues.withIndex()) {
                serializeQueueFeature(component, wrappedFeature, queue, position)
            }
        }

        for (queue in mapping.queues) {
            serializeQueueClass(component, queue)
        }
    }

    // a queue at the root receives from the environment unless it stores a clock; a composite decides per instance
    private fun IndentingStringBuilder.serializeQueueFeature(
        adapter: ComponentDeclaration,
        wrappedFeature: String,
        queue: QueueMapping,
        position: Int,
    ) {
        val flag = environmentalFlag(queue)
        appendLine("refers $flag: bool = ${!queue.storesClocks && queue.portMessages.isNotEmpty()}")
        appendIndent("contains ${queueFeatureName(adapter, queue)}: ${queueClassName(adapter, queue)} subsets queues") {
            appendLine("redefine refers position: int = $position")
            appendLine("redefine refers rank: int = ${queue.rank}")
            appendLine("redefine refers environmental: bool = $flag")
            for (message in queue.messages) {
                val identifier = message.identifier
                if (message.port != null && message.event != null) {
                    val portPath = adapterPortPath(adapter, message.port)
                    appendLine("redefine refers $PORT_REFERENCE$identifier: Port = $portPath")
                    appendLine("redefine refers $SOURCE_REFERENCE$identifier: Event = ${inputEventPath(message.port, message.event, portPath)}")
                }
                if (message.targetPort != null && message.targetEvent != null) {
                    val eventPath = inputEventPath(message.targetPort, message.targetEvent, "$wrappedFeature.${markName(message.targetPort.name)}")
                    appendLine("redefine refers $EVENT_REFERENCE$identifier: Event = $eventPath")
                    for (parameter in message.targetEvent.parameters) {
                        appendLine("redefine refers $PARAMETER_REFERENCE$identifier${markName(parameter.name)}: Parameter = $eventPath.${markName(parameter.name)}")
                    }
                }
                if (message.clock != null) {
                    appendLine("redefine refers $CLOCK_REFERENCE$identifier: Clock = ${markName(message.clock.name)}")
                }
            }
        }
    }

    private fun storedParameters(message: QueuedMessage): List<ParameterDeclaration> {
        return message.event?.parameters.orEmpty()
    }

    private fun IndentingStringBuilder.serializeQueueClass(adapter: ComponentDeclaration, queue: QueueMapping) {
        val capacity = queue.capacity
        appendIndent("class ${queueClassName(adapter, queue).substringAfterLast("::")} : MessageQueue") {
            appendLine("redefine refers capacity: int = $capacity")
            appendLine("redefine refers priority: int = ${queue.priority}")
            if (queue.discardsOldest) {
                appendLine("redefine refers discardsOldest: bool = true")
            }
            appendLine()
            for (message in queue.messages) {
                val identifier = message.identifier
                if (message.port != null) {
                    appendLine("refers $PORT_REFERENCE$identifier: Port")
                    appendLine("refers $SOURCE_REFERENCE$identifier: Event")
                }
                if (message.targetEvent != null) {
                    appendLine("refers $EVENT_REFERENCE$identifier: Event")
                    for (parameter in message.targetEvent.parameters) {
                        appendLine("refers $PARAMETER_REFERENCE$identifier${markName(parameter.name)}: Parameter")
                    }
                }
                if (message.clock != null) {
                    appendLine("refers $CLOCK_REFERENCE$identifier: Clock")
                }
            }
            appendLine()
            // a size of one reads as a multiplicity, so a queue of one message gets a second cell it never uses
            val length = maxOf(capacity, 2)
            appendLine("var $IDENTIFIERS: int[$length] := ${arrayLiteral(length, "0")}")
            for (message in queue.messages) {
                for (parameter in storedParameters(message)) {
                    val type = oxstsType(parameter.type)
                    appendLine("var ${valuesName(message, parameter)}: $type[$length] := ${arrayLiteral(length, defaultValue(parameter.type))}")
                }
            }
            appendLine()
            serializeReceive(queue)
            appendLine()
            serializeShift(queue)
            appendLine()
            serializeClear(queue)
            for (message in queue.messages) {
                appendLine()
                serializeEnqueue(message)
            }
            appendLine()
            serializeEnvironmentMessage(queue)
            appendLine()
            serializeClockTick(queue)
        }
    }

    private fun arrayLiteral(capacity: Int, value: String): String {
        return List(capacity) {
            value
        }.joinToString(", ", "[", "]")
    }

    private fun valuesName(message: QueuedMessage, parameter: ParameterDeclaration): String {
        return "$VALUES_ARRAY${message.identifier}${markName(parameter.name)}"
    }

    private fun IndentingStringBuilder.serializeReceive(queue: QueueMapping) {
        appendIndent("redefine tran receive()") {
            appendLine("var $IDENTIFIER: int := $IDENTIFIERS[0]")
            for (message in queue.messages) {
                val targetEvent = message.targetEvent ?: continue
                appendIndent("if ($IDENTIFIER == ${message.identifier})") {
                    appendLine("$EVENT_REFERENCE${message.identifier}.isRaised := true")
                    for ((index, parameter) in targetEvent.parameters.withIndex()) {
                        val stored = storedParameters(message)[index]
                        appendLine("inline $PARAMETER_REFERENCE${message.identifier}${markName(parameter.name)}.set(${valuesName(message, stored)}[0])")
                    }
                }
            }
            appendLine("inline $SHIFT_TRANSITION()")
            val controls = queue.messages.groupBy {
                it.control
            }
            for ((control, messages) in controls) {
                val call = when (control) {
                    MessageControl.NONE -> continue
                    MessageControl.RUN -> "inline adapter.run()"
                    MessageControl.CLEAR_QUEUE -> "inline clear()"
                    MessageControl.CLEAR_QUEUES -> "inline adapter.clearQueues()"
                    MessageControl.CLEAR_OTHER_QUEUES -> "inline adapter.clearOtherQueues(self)"
                }
                if (messages.size == queue.messages.size) {
                    appendLine(call)
                    continue
                }
                val condition = messages.joinToString(" || ") {
                    "$IDENTIFIER == ${it.identifier}"
                }
                appendIndent("if ($condition)") {
                    appendLine(call)
                }
            }
        }
    }

    private fun IndentingStringBuilder.serializeShift(queue: QueueMapping) {
        val arrays = listOf(IDENTIFIERS to "0") + queue.messages.flatMap { message ->
            storedParameters(message).map {
                valuesName(message, it) to defaultValue(it.type)
            }
        }
        appendIndent("redefine tran $SHIFT_TRANSITION()") {
            appendIndent("inline for (index in 0..<capacity - 1)") {
                for ((array, _) in arrays) {
                    appendLine("$array[index] := $array[index + 1]")
                }
            }
            for ((array, default) in arrays) {
                appendLine("$array[capacity - 1] := $default")
            }
            appendLine("size := size - 1")
        }
    }

    private fun IndentingStringBuilder.serializeClear(queue: QueueMapping) {
        val arrays = listOf(IDENTIFIERS to "0") + queue.messages.flatMap { message ->
            storedParameters(message).map {
                valuesName(message, it) to defaultValue(it.type)
            }
        }
        appendIndent("redefine tran clear()") {
            appendIndent("inline for (index in 0..<capacity)") {
                for ((array, default) in arrays) {
                    appendLine("$array[index] := $default")
                }
            }
            appendLine("size := 0")
        }
    }

    private fun IndentingStringBuilder.serializeEnqueue(message: QueuedMessage) {
        val parameters = storedParameters(message).joinToString(", ") {
            "$VALUE_PARAMETER${markName(it.name)}: ${oxstsType(it.type)}"
        }
        appendIndent("tran $ENQUEUE_TRANSITION${message.identifier}($parameters)") {
            appendLine("$IDENTIFIERS[size] := ${message.identifier}")
            for (parameter in storedParameters(message)) {
                appendLine("${valuesName(message, parameter)}[size] := $VALUE_PARAMETER${markName(parameter.name)}")
            }
            appendLine("size := size + 1")
        }
    }

    // the environment adds one message of an event of the queue, or none
    private fun IndentingStringBuilder.serializeEnvironmentMessage(queue: QueueMapping) {
        appendIndent("redefine tran receiveEnvironmentMessage()") {
            if (queue.portMessages.isEmpty()) {
                return@appendIndent
            }
            appendLine("choice {")
            for (message in queue.portMessages) {
                appendLine("} or {")
                indented {
                    val parameters = storedParameters(message)
                    for (parameter in parameters) {
                        val local = "$VALUE_PARAMETER${markName(parameter.name)}"
                        appendLine("var $local: ${oxstsType(parameter.type)} := ${defaultValue(parameter.type)}")
                        appendLine("havoc($local)")
                    }
                    appendLine("inline $PORT_REFERENCE${message.identifier}.receiveInputMessage($SOURCE_REFERENCE${message.identifier})")
                    val arguments = parameters.joinToString(", ") {
                        "$VALUE_PARAMETER${markName(it.name)}"
                    }
                    appendLine("inline $ENQUEUE_TRANSITION${message.identifier}($arguments)")
                }
            }
            appendLine("}")
        }
    }

    private fun IndentingStringBuilder.serializeClockTick(queue: QueueMapping) {
        appendIndent("redefine tran receiveClockTick(clock: Clock)") {
            for (message in queue.messages) {
                if (message.clock == null) {
                    continue
                }
                appendIndent("inline if (clock == $CLOCK_REFERENCE${message.identifier})") {
                    appendLine("inline dropOldestWhenFull()")
                    appendIndent("if (!isFull())") {
                        appendLine("inline $ENQUEUE_TRANSITION${message.identifier}()")
                    }
                }
            }
        }
    }

    private fun IndentingStringBuilder.serializeAsynchronousComposite(component: CompositeComponentDeclaration, superclass: String) {
        val dispatches = AsynchronousMapping.dispatchesOf(component)
        val dispatchNames = mutableMapOf<DispatchMapping, String>()
        val taken = mutableSetOf<String>()
        for (dispatch in dispatches) {
            val derived = "$DISPATCH_CLASS${component.name}_${dispatch.sourceInstance.name}_${dispatch.sourcePort.name}_${dispatch.event.name}_to_${dispatch.targetInstance.name}_${dispatch.targetPort.name}"
            var name = derived
            var number = 2
            while (!taken.add(name)) {
                name = "${derived}_$number"
                number++
            }
            dispatchNames[dispatch] = name
        }

        appendIndent("class ${markName(component.name)} : $superclass") {
            val boundPorts = component.bindings.map {
                it.port
            }.toSet()
            for (port in component.ports - boundPorts) {
                serialize(port)
            }

            for (portBinding in component.bindings) {
                serialize(portBinding)
            }

            for (instance in component.components) {
                serializeAdapterInstance(component, instance)
            }

            for (dispatch in dispatches) {
                serializeDispatchFeature(dispatch, dispatchNames.getValue(dispatch))
            }

            if (component is ScheduledAsynchronousCompositeComponentDeclaration) {
                serializeAsynchronousExecutionSteps(component)
            }
        }

        for (dispatch in dispatches) {
            serializeDispatchClass(dispatch, dispatchNames.getValue(dispatch))
        }
    }

    private fun IndentingStringBuilder.serializeAdapterInstance(composite: CompositeComponentDeclaration, instance: ComponentInstance) {
        val name = nameMap.declareTyped(composite, instance, markName(instance.name), instance.component)
        val settings = mutableListOf<String>()
        val scheduleCount = AsynchronousMapping.scheduleCount(composite, instance)
        if (scheduleCount != 1) {
            settings += "redefine refers scheduleCount: int = $scheduleCount"
        }
        for (queue in AsynchronousMapping.adapterOf(instance.component).queues) {
            val environmental = queue.portMessages.isNotEmpty() && queue.portMessages.all {
                AsynchronousMapping.isBound(composite, instance, it.port!!)
            }
            settings += "redefine refers ${environmentalFlag(queue)}: bool = $environmental"
        }
        appendIndent("contains $name: ${componentClassName(instance.component)} subsets adapters") {
            for (setting in settings) {
                appendLine(setting)
            }
        }
    }

    private fun IndentingStringBuilder.serializeDispatchFeature(dispatch: DispatchMapping, name: String) {
        val source = markName(dispatch.sourceInstance.name)
        val target = markName(dispatch.targetInstance.name)
        val eventPath = outputEventPath(dispatch.sourcePort, dispatch.event, "$source.${adapterPortPath(dispatch.sourceInstance.component, dispatch.sourcePort)}")
        appendIndent("contains $name: ${markQualifiedName(gammaPackageOf(dispatch.sourceInstance).name)}::$name subsets dispatches") {
            appendLine("redefine refers source: AsynchronousAdapter = $source")
            appendLine("redefine refers event: Event = $eventPath")
            appendLine("redefine refers target: ${queueClassName(dispatch.targetInstance.component, dispatch.queue)} = $target.${queueFeatureName(dispatch.targetInstance.component, dispatch.queue)}")
            for (parameter in dispatch.event.parameters) {
                appendLine("redefine refers $DISPATCHED_PARAMETER${markName(parameter.name)}: Parameter = $eventPath.${markName(parameter.name)}")
            }
            if (!dispatch.resetsSource) {
                appendLine("redefine refers resetsSource: bool = false")
            }
        }
    }

    private fun IndentingStringBuilder.serializeDispatchClass(dispatch: DispatchMapping, name: String) {
        appendIndent("class $name : MessageDispatch") {
            appendLine("redefine refers target: ${queueClassName(dispatch.targetInstance.component, dispatch.queue)}")
            for (parameter in dispatch.event.parameters) {
                appendLine("refers $DISPATCHED_PARAMETER${markName(parameter.name)}: Parameter")
            }
            appendLine()
            appendIndent("redefine tran enqueue()") {
                val arguments = dispatch.event.parameters.joinToString(", ") {
                    "$DISPATCHED_PARAMETER${markName(it.name)}.evaluate() as ${oxstsType(it.type)}"
                }
                appendLine("inline target.$ENQUEUE_TRANSITION${dispatch.message.identifier}($arguments)")
            }
        }
    }

    private fun IndentingStringBuilder.serializeAsynchronousExecutionSteps(component: ScheduledAsynchronousCompositeComponentDeclaration) {
        val executionList = component.executionList.ifEmpty {
            component.components
        }
        val taken = mutableSetOf<String>()
        for (instance in executionList) {
            val derived = "$EXECUTION_STEP${markName(instance.name)}"
            var name = derived
            var number = 2
            while (!taken.add(name)) {
                name = "${derived}_$number"
                number++
            }
            appendIndent("contains $name: AsynchronousExecutionStep subsets executionSteps") {
                appendLine("redefine refers adapter: AsynchronousAdapter = ${markName(instance.name)}")
            }
        }
    }

    private fun IndentingStringBuilder.serializeComposite(component: CompositeComponentDeclaration, superclass: String) {
        appendIndent("class ${markName(component.name)} : $superclass") {
            val unboundPorts = mutableSetOf<PortDeclaration>()
            unboundPorts += component.ports
            unboundPorts -= component.bindings.map { it.port }.toSet()

            for (port in unboundPorts) {
                serialize(port)
            }

            for (portBinding in component.bindings) {
                serialize(portBinding)
            }

            for (instance in component.components) {
                serialize(instance)
            }

            for (channel in component.channels) {
                serialize(channel)
            }

            if (component is CascadeComponentDeclaration) {
                serializeExecutionSteps(component)
            }
        }
    }

    private fun IndentingStringBuilder.serializeExecutionSteps(component: CascadeComponentDeclaration) {
        val executionList = component.executionList.ifEmpty {
            component.components
        }
        val taken = mutableSetOf<String>()
        for (instance in executionList) {
            val derived = "$EXECUTION_STEP${markName(instance.name)}"
            var name = derived
            var number = 2
            while (!taken.add(name)) {
                name = "${derived}_$number"
                number++
            }
            appendIndent("contains $name: ExecutionStep subsets executionSteps") {
                appendLine("redefine refers component: Component = ${markName(instance.name)}")
            }
        }
    }

    private fun getPortType(port: PortDeclaration): String {
        return when (port.realizationMode) {
            RealizationMode.PROVIDED -> "ProvidedPort"
            RealizationMode.REQUIRED -> "RequiredPort"
        }
    }

    private fun IndentingStringBuilder.serialize(port: PortDeclaration) {
        val name = nameMap.declare(port.eContainer(), port, markName(port.name))
        appendIndent("contains $name: ${getPortType(port)} subsets ports") {
            val interfaceName = nameMap.declare(port, port.`interface`, "interface")
            appendLine("redefine contains $interfaceName: ${qualifiedClassName(port.`interface`)}")
        }
    }

    private fun IndentingStringBuilder.serialize(portBinding: PortBinding) {
        val name = nameMap.declare(portBinding.eContainer(), portBinding.port, markName(portBinding.port.name))
        appendLine("refers $name: Port subsets ports = ${instancePortPath(portBinding.boundPort)}")
    }

    private fun IndentingStringBuilder.serialize(instance: ComponentInstance) {
        val name = nameMap.declareTyped(instance.eContainer(), instance, markName(instance.name), instance.component)
        appendLine("contains $name: ${componentClassName(instance.component)} subsets components")
    }

    private fun IndentingStringBuilder.serialize(channel: Channel) {
        val name = nameMap.declareGenerated(channel.eContainer(), channel, deriveName(channel))
        appendIndent("contains $name: Channel subsets channels") {
            appendLine("redefine refers inputPort: Port = ${instancePortPath(channel.providedPort)}")
            appendLine("redefine refers outputPort: Port = ${instancePortPath(channel.requiredPort)}")
        }
    }

    private fun instancePortPath(reference: InstancePortReference): String {
        return "${markName(reference.instance.name)}.${markName(reference.port.name)}"
    }

    private fun qualifiedClassName(element: NamedElement): String {
        val gammaPackage = EcoreUtil2.getContainerOfType(element, GammaModelPackage::class.java)
        return "${markQualifiedName(gammaPackage.name)}::${markName(element.name)}"
    }

    private fun getRegionSchedule(schedulingOrder: SchedulingOrder): String {
        return when (schedulingOrder) {
            SchedulingOrder.TOP_DOWN -> "TopDown"
            SchedulingOrder.BOTTOM_UP -> "BottomUp"
        }
    }

    private fun getTransitionPriority(transitionPriority: TransitionPriority): String {
        return when (transitionPriority) {
            TransitionPriority.OFF -> "Off"
            TransitionPriority.ORDER_BASED -> "OrderBased"
            TransitionPriority.VALUE_BASED -> "ValueBased"
        }
    }

    private fun getGuardEvaluation(guardEvaluation: GuardEvaluation): String {
        return when (guardEvaluation) {
            GuardEvaluation.ON_THE_FLY -> "OnTheFly"
            GuardEvaluation.BEGINNING_OF_STEP -> "BeginningOfStep"
        }
    }

    private fun IndentingStringBuilder.serialize(statechart: StatechartDeclaration) {
        appendIndent("class ${markName(statechart.name)} : Statechart") {
            appendLine("redefine refers regionSchedule: RegionSchedule = RegionSchedule::${getRegionSchedule(statechart.schedulingOrder)}")
            appendLine("redefine refers transitionPriority: TransitionPriority = TransitionPriority::${getTransitionPriority(statechart.transitionPriority)}")
            appendLine("redefine refers guardEvaluation: GuardEvaluation = GuardEvaluation::${getGuardEvaluation(statechart.guardEvaluation)}")

            for (port in statechart.ports) {
                serialize(port)
            }

            for (variable in statechart.variables) {
                serialize(variable)
            }

            for (timeout in statechart.timeouts) {
                serialize(timeout)
            }

            for (region in statechart.regions) {
                serialize(region)
            }
        }
    }

    private fun IndentingStringBuilder.serialize(variable: VariableDeclaration) {
        val name = nameMap.declare(variable.eContainer(), variable, markName(variable.name))
        appendIndent("contains $name: ${variableClassName(variable.type)} subsets variables") {
            val default = variable.default ?: return@appendIndent
            if (default is RecordLiteralExpression) {
                val recordType = recordTypeOf(variable.type) ?: error("Variable '${variable.name}' is not of a record type")
                val bindings = defaultBindings(recordType)
                for (leaf in literalLeaves(default, emptyList())) {
                    val binding = bindings.single {
                        it.path == leaf.path
                    }
                    appendLine("redefine refers ${binding.name}: ${oxstsType(leaf.path.last().type)} = ${serializeInline(leaf.value)}")
                }
            } else {
                appendLine("redefine refers defaultValue: ${oxstsType(variable.type)} = ${serializeInline(default)}")
            }
        }
    }

    private class LiteralLeaf(
        val path: List<FieldDeclaration>,
        val value: Expression,
    )

    private fun literalLeaves(literal: RecordLiteralExpression, prefix: List<FieldDeclaration>): List<LiteralLeaf> {
        return literal.fieldAssignments.flatMap {
            val path = prefix + it.field
            val value = it.value
            if (value is RecordLiteralExpression) {
                literalLeaves(value, path)
            } else {
                listOf(LiteralLeaf(path, value))
            }
        }
    }

    private fun IndentingStringBuilder.serialize(timeout: TimeoutDeclaration) {
        val name = nameMap.declare(timeout.eContainer(), timeout, markName(timeout.name))
        if (isSetInMilliseconds(timeout)) {
            appendIndent("contains $name: Timeout subsets timeouts") {
                appendLine("redefine refers deltaTime: int = $MILLISECONDS_PER_STEP")
            }
        } else {
            appendLine("contains $name: Timeout subsets timeouts")
        }
    }

    // the validator lets a timeout be set to one value only, so its settings share one unit
    private fun isSetInMilliseconds(timeout: TimeoutDeclaration): Boolean {
        val statechart = EcoreUtil2.getContainerOfType(timeout, StatechartDeclaration::class.java)
        return EcoreUtil2.getAllContentsOfType(statechart, SetTimeoutAction::class.java).any {
            it.timeout == timeout && it.unit == TimeUnit.MILLISECOND
        }
    }

    private fun IndentingStringBuilder.serialize(region: Region) {
        val name = nameMap.declare(region.eContainer(), region, markName(region.name))
        appendIndent("contains $name: Region subsets regions") {
            for (state in region.stateNodes.filterIsInstance<State>()) {
                serialize(state)
            }

            for (history in region.stateNodes.filterIsInstance<ShallowHistoryState>()) {
                serialize(history)
            }

            for (transition in transitionsOf(region)) {
                serialize(region, transition)
            }
        }
    }

    private fun IndentingStringBuilder.serialize(state: State) {
        val name = nameMap.declare(state.eContainer(), state, markName(state.name))
        appendIndent("contains $name: State subsets states") {
            for (action in state.entryActions) {
                serialize(action, "entryActions")
            }

            for (action in state.exitActions) {
                serialize(action, "exitActions")
            }

            for (region in state.regions) {
                serialize(region)
            }
        }
    }

    // the node's own transition is its default target, which the library holds on the node
    private fun IndentingStringBuilder.serialize(history: ShallowHistoryState) {
        val name = nameMap.declare(history.eContainer(), history, markName(history.name))
        val statechart = EcoreUtil2.getContainerOfType(history, StatechartDeclaration::class.java)
        val target = statechart.transitions.single {
            it.from == history
        }.to
        appendIndent("contains $name: ShallowHistory subsets shallowHistory") {
            appendLine("redefine refers to: State = ${markName(target.name)}")
        }
    }

    private fun transitionsOf(region: Region): List<Transition> {
        val statechart = EcoreUtil2.getContainerOfType(region, StatechartDeclaration::class.java)
        return statechart.transitions.filter {
            it.from.eContainer() == region
        }
    }

    private fun IndentingStringBuilder.serialize(region: Region, transition: Transition) {
        when (transition.from) {
            is InitialState -> serializeEntryTransition(region, transition)
            is ShallowHistoryState -> Unit
            else -> serializeStateTransition(region, transition)
        }
    }

    private fun IndentingStringBuilder.serializeEntryTransition(region: Region, transition: Transition) {
        val name = nameMap.declareGenerated(region, transition, "entry_to_${transition.to.name}")
        appendIndent("contains $name: EntryTransition subsets entryTransitions") {
            appendLine("redefine refers to: State = ${markName(transition.to.name)}")
            for (action in transition.actions) {
                serialize(action, "actions")
            }
        }
    }

    private fun IndentingStringBuilder.serializeStateTransition(region: Region, transition: Transition) {
        val name = if (transition.name != null) {
            nameMap.declare(region, transition, markName(transition.name))
        } else {
            nameMap.declareGenerated(region, transition, deriveName(region, transition))
        }
        appendIndent("contains $name: Transition subsets transitions") {
            appendLine("redefine refers from: State = ${markName(transition.from.name)}")
            val targetType = if (transition.to is ShallowHistoryState) {
                "ShallowHistory"
            } else {
                "State"
            }
            appendLine("redefine refers to: $targetType = ${targetPath(transition).joinToString(".")}")

            val statechart = EcoreUtil2.getContainerOfType(transition, StatechartDeclaration::class.java)
            if (statechart.transitionPriority == TransitionPriority.VALUE_BASED && transition.priority != 0) {
                appendLine("redefine refers priority: int = ${transition.priority}")
            }

            if (transition.guard != null) {
                appendIndent("redefine contains guard: Guard") {
                    serialize(transition.guard, "expression")
                }
            }

            if (transition.trigger != null) {
                serialize(transition.trigger)
            }

            for (action in transition.actions) {
                serialize(action, "actions")
            }
        }
    }

    private fun IndentingStringBuilder.serialize(trigger: Trigger) = when (trigger) {
        is EventTrigger -> serialize(trigger)
        is TimeoutTrigger -> serialize(trigger)
        else -> error("Unknown trigger type: $trigger")
    }

    // a target in another region is named from the lowest region holding both ends, which the transition's own scope reaches
    private fun targetPath(transition: Transition): List<String> {
        val sourceRegions = ancestorRegions(transition.from)
        val path = mutableListOf(markName(transition.to.name))
        var region = transition.to.eContainer() as Region
        while (region !in sourceRegions) {
            val state = region.eContainer() as State
            path.add(0, markName(region.name))
            path.add(0, markName(state.name))
            region = state.eContainer() as Region
        }
        return path
    }

    private fun ancestorRegions(stateNode: StateNode): Set<Region> {
        val regions = mutableSetOf<Region>()
        var current: EObject = stateNode.eContainer()
        while (current is Region) {
            regions += current
            current = current.eContainer()
            if (current is State) {
                current = current.eContainer()
            }
        }
        return regions
    }

    private fun IndentingStringBuilder.serialize(trigger: EventTrigger) {
        appendIndent("redefine contains trigger: EventTrigger") {
            appendLine("redefine refers port: Port = ${memberReference(trigger.port)}")
            appendLine("redefine refers event: Event = ${inputEventPath(trigger.port, trigger.event)}")
        }
    }

    // a provided port receives the input half of an inout event and raises its output half, a required port the reverse
    private fun inputEventPath(
        port: PortDeclaration,
        event: EventDeclaration,
        portReference: String = memberReference(port),
    ): String {
        return eventPath(portReference, event, port.realizationMode == RealizationMode.PROVIDED)
    }

    private fun outputEventPath(
        port: PortDeclaration,
        event: EventDeclaration,
        portReference: String = memberReference(port),
    ): String {
        return eventPath(portReference, event, port.realizationMode == RealizationMode.REQUIRED)
    }

    private fun eventPath(
        portReference: String,
        event: EventDeclaration,
        inputHalf: Boolean,
    ): String {
        val halves = eventHalves(event)
        val half = if (halves.size == 1 || inputHalf) {
            halves.first()
        } else {
            halves.last()
        }
        return "$portReference.interface.${half.name}"
    }

    private fun IndentingStringBuilder.serialize(trigger: TimeoutTrigger) {
        appendIndent("redefine contains trigger: TimeoutTrigger") {
            appendLine("redefine refers timeout: Timeout = ${memberReference(trigger.timeout)}")
        }
    }

    private fun IndentingStringBuilder.serialize(
        action: Action,
        subsets: String,
    ): Unit = when (action) {
        is Block -> {
            for (inner in action.actions) {
                serialize(inner, subsets)
            }
        }
        is RaiseEventAction -> serialize(action, subsets)
        is SetTimeoutAction -> serialize(action, subsets)
        is AssignmentAction -> serialize(action, subsets)
        else -> error("Unknown action type: $action")
    }

    // a generated name starts with a lowercase word, so it never equals a marked Gamma name
    private fun deriveName(channel: Channel): String {
        val provided = "${channel.providedPort.instance.name}_${channel.providedPort.port.name}"
        val required = "${channel.requiredPort.instance.name}_${channel.requiredPort.port.name}"
        return "channel_${provided}_to_$required"
    }

    private fun deriveName(region: Region, transition: Transition): String {
        val base = "transition_${transition.from.name}_to_${transition.to.name}"
        val siblings = transitionsOf(region)
        val collides = siblings.any {
            it !== transition && it.name == null && it.from == transition.from && it.to == transition.to
        }
        if (!collides || transition.trigger == null) {
            return base
        }

        return "${base}_on_${deriveName(transition.trigger)}"
    }

    private fun deriveName(trigger: Trigger) = when (trigger) {
        is EventTrigger -> "${trigger.port.name}_${trigger.event.name}"
        is TimeoutTrigger -> trigger.timeout.name
        else -> error("Unknown trigger type: $trigger")
    }

    private fun deriveName(
        action: Action,
        subsets: String,
    ): String {
        val prefix = when (subsets) {
            "entryActions" -> "entry_"
            "exitActions" -> "exit_"
            else -> ""
        }
        val derived = when (action) {
            is RaiseEventAction -> "raise_${action.port.name}_${action.event.name}"
            is SetTimeoutAction -> "set_${action.timeout.name}"
            is AssignmentAction -> "assign_${assignedVariable(action).name}"
            else -> error("Unknown action type: $action")
        }
        return "$prefix$derived"
    }

    private fun IndentingStringBuilder.serialize(
        action: RaiseEventAction,
        subsets: String,
    ) {
        val name = nameMap.declareGenerated(action.eContainer(), action, deriveName(action, subsets))
        val eventPath = outputEventPath(action.port, action.event)
        appendIndent("contains $name: RaiseEventAction subsets $subsets") {
            appendLine("redefine refers port: Port = ${memberReference(action.port)}")
            appendLine("redefine refers event: Event = $eventPath")
            for ((index, argument) in action.arguments.withIndex()) {
                val parameter = action.event.parameters[index]
                appendIndent("contains ${markName(parameter.name)}: Argument subsets arguments") {
                    appendLine("redefine refers parameter: Parameter = $eventPath.${markName(parameter.name)}")
                    serialize(argument, "expression")
                }
            }
        }
    }

    private fun IndentingStringBuilder.serialize(
        action: SetTimeoutAction,
        subsets: String,
    ) {
        val name = nameMap.declareGenerated(action.eContainer(), action, deriveName(action, subsets))
        appendIndent("contains $name: SetTimeoutAction subsets $subsets") {
            appendLine("redefine refers timeout: Timeout = ${memberReference(action.timeout)}")
            serialize(action.value, "expression")
        }
    }

    private fun IndentingStringBuilder.serialize(
        action: AssignmentAction,
        subsets: String,
    ) {
        val name = nameMap.declareGenerated(action.eContainer(), action, deriveName(action, subsets))
        appendIndent("contains $name: AssignmentAction subsets $subsets") {
            appendLine("redefine refers variable: Variable = ${memberReference(assignedVariable(action))}")
            serializeAssignedValue(fieldAccesses(action.reference), action.expression, "expression")
        }
    }

    // a field write assigns the whole variable the record with that field updated, outermost field first
    private fun IndentingStringBuilder.serializeAssignedValue(
        accesses: List<RecordAccessExpression>,
        value: Expression,
        redefines: String,
    ) {
        if (accesses.isEmpty()) {
            serialize(value, redefines)
            return
        }
        appendIndent("redefine contains $redefines: FieldUpdateExpression") {
            serialize(accesses.first(), "field")
            serializeAssignedValue(accesses.drop(1), value, "value")
        }
    }

    private fun fieldAccesses(reference: Expression): List<RecordAccessExpression> {
        val accesses = mutableListOf<RecordAccessExpression>()
        var current = reference
        while (current is RecordAccessExpression) {
            accesses.add(0, current)
            current = current.operand
        }
        return accesses
    }

    private fun assignedVariable(action: AssignmentAction): VariableDeclaration {
        var reference = action.reference
        while (reference is RecordAccessExpression) {
            reference = reference.operand
        }
        return (reference as DirectReferenceExpression).declaration as VariableDeclaration
    }

    private fun IndentingStringBuilder.serialize(verificationCase: VerificationCaseDeclaration) {
        appendLine("@VerificationCase")
        appendIndent("class ${markName(verificationCase.name)} : GammaVerificationCase") {
            val component = verificationCase.component
            nameMap.declareTyped(verificationCase, component, CASE_COMPONENT, component)
            appendLine("redefine contains $CASE_COMPONENT: ${componentClassName(component)}")

            serialize(verificationCase.expression, "propertyExpression")
        }
    }

    private fun IndentingStringBuilder.serialize(
        expression: Expression,
        redefines: String,
    ) = when (expression) {
        is ArithmeticBinaryOperator -> serialize(expression, redefines)
        is ArithmeticUnaryOperator -> serialize(expression, redefines)
        is BooleanOperator -> serialize(expression, redefines)
        is TemporalOperator -> serialize(expression, redefines)
        is ComparisonOperator -> serialize(expression, redefines)
        is LiteralExpression -> serialize(expression, redefines)
        is NegationOperator -> serialize(expression, redefines)
        is StateReferenceExpression -> serialize(expression, redefines)
        is RaiseEventAct -> serialize(expression, redefines)
        is DirectReferenceExpression -> serializeVariableReference(expression, redefines)
        is VariableReferenceExpression -> serializeVariableReference(expression, redefines)
        is EventParameterReferenceExpression -> serialize(expression, redefines)
        is RecordAccessExpression -> serialize(expression, redefines)
        else -> error("Unknown type of expression: $expression")
    }

    private fun getOperatorName(operator: ArithmeticOp): String {
        return when (operator) {
            ArithmeticOp.ADD -> "PlusOperatorExpression"
            ArithmeticOp.SUB -> "MinusOperatorExpression"
            ArithmeticOp.MUL -> "MultiplyOperatorExpression"
            ArithmeticOp.DIV -> "DivideOperatorExpression"
        }
    }

    private fun getOperatorName(operator: UnaryOp): String {
        return when (operator) {
            UnaryOp.PLUS -> "UnaryPlusExpression"
            UnaryOp.MINUS -> "UnaryMinusExpression"
        }
    }

    private fun getOperatorName(operator: BooleanOp): String {
        return when (operator) {
            BooleanOp.AND -> "AndOperatorExpression"
            BooleanOp.OR -> "OrOperatorExpression"
        }
    }

    private fun getOperatorName(operator: ComparisonOp): String {
        return when (operator) {
            ComparisonOp.LESS -> "LessThanOperatorExpression"
            ComparisonOp.LESS_EQ -> "LessThanOrEqualsOperatorExpression"
            ComparisonOp.GREATER -> "GreaterThanOperatorExpression"
            ComparisonOp.GREATER_EQ -> "GreaterThanOrEqualsOperatorExpression"
            ComparisonOp.EQ -> "EqualityOperatorExpression"
            ComparisonOp.NOT_EQ -> "InequalityOperatorExpression"
        }
    }

    private fun IndentingStringBuilder.serialize(
        expression: ArithmeticBinaryOperator,
        redefines: String,
    ) {
        serializeOperator(expression.left, expression.right, getOperatorName(expression.op), redefines)
    }

    private fun IndentingStringBuilder.serialize(
        expression: ArithmeticUnaryOperator,
        redefines: String,
    ) {
        serializeOperator(expression.body, getOperatorName(expression.op), redefines)
    }

    private fun IndentingStringBuilder.serialize(
        expression: BooleanOperator,
        redefines: String,
    ) {
        serializeOperator(expression.left, expression.right, getOperatorName(expression.op), redefines)
    }

    private fun IndentingStringBuilder.serialize(
        expression: TemporalOperator,
        redefines: String,
    ) {
        when (expression) {
            is AG -> serialize(expression, redefines)
            is EF -> serialize(expression, redefines)
            else -> error("Unknown type of temporal expression: $expression")
        }
    }

    private fun IndentingStringBuilder.serialize(
        expression: AG,
        redefines: String,
    ) {
        serializeOperator(expression.body, "MustAlways", redefines)
    }

    private fun IndentingStringBuilder.serialize(
        expression: EF,
        redefines: String,
    ) {
        serializeOperator(expression.body, "Eventually", redefines)
    }

    private fun IndentingStringBuilder.serialize(
        expression: ComparisonOperator,
        redefines: String,
    ) {
        serializeOperator(expression.left, expression.right, getOperatorName(expression.op), redefines)
    }

    private fun IndentingStringBuilder.serializeOperator(
        left: Expression,
        right: Expression,
        name: String,
        redefines: String,
    ) {
        appendIndent("redefine contains $redefines: $name") {
            serialize(left, "left")
            serialize(right, "right")
        }
    }

    private fun IndentingStringBuilder.serializeOperator(
        body: Expression,
        name: String,
        redefines: String,
    ) {
        appendIndent("redefine contains $redefines: $name") {
            serialize(body, "body")
        }
    }

    private fun IndentingStringBuilder.serialize(
        expression: LiteralExpression,
        redefines: String,
    ) {
        when (expression) {
            is LiteralInteger -> serialize(expression, redefines)
            is LiteralBoolean -> serialize(expression, redefines)
            else -> error("")
        }
    }

    private fun IndentingStringBuilder.serialize(
        expression: LiteralInteger,
        redefines: String,
    ) {
        appendIndent("redefine contains $redefines: LiteralIntegerExpression") {
            appendLine("redefine refers value: int = ${expression.value}")
        }
    }

    private fun IndentingStringBuilder.serialize(
        expression: LiteralBoolean,
        redefines: String,
    ) {
        appendIndent("redefine contains $redefines: LiteralBooleanExpression") {
            appendLine("redefine refers value: bool = ${expression.isValue}")
        }
    }

    private fun IndentingStringBuilder.serialize(
        expression: NegationOperator,
        redefines: String,
    ) {
        serializeOperator(expression.body, "UnaryNotExpression", redefines)
    }

    private fun IndentingStringBuilder.serialize(
        expression: StateReferenceExpression,
        redefines: String,
    ) {
        val path = instancePath(expression) + containmentPath(expression.state)
        appendIndent("redefine contains $redefines: IsStateActiveExpression") {
            appendLine("redefine refers state: State = ${path.joinToString(".")}")
        }
    }

    private fun IndentingStringBuilder.serialize(
        expression: RaiseEventAct,
        redefines: String,
    ) {
        // a bound port is a reference typed Port, so the event is named on the instance port the binding reaches
        val path = instancePath(expression).toMutableList()
        var component = GammaUtils.getReferencedComponent(expression)
        var port = expression.port
        while (component is CompositeComponentDeclaration) {
            val binding = component.bindings.firstOrNull {
                it.port == port
            } ?: break
            path += markName(binding.boundPort.instance.name)
            component = binding.boundPort.instance.component
            port = binding.boundPort.port
        }
        val portPath = (path + markName(port.name)).joinToString(".")
        appendIndent("redefine contains $redefines: IsEventRaisedExpression") {
            appendLine("redefine refers port: Port = $portPath")
            appendLine("redefine refers event: Event = ${outputEventPath(port, expression.event, portPath)}")
        }
    }

    private fun IndentingStringBuilder.serialize(
        expression: EventParameterReferenceExpression,
        redefines: String,
    ) {
        val eventPath = inputEventPath(expression.port, expression.event)
        appendIndent("redefine contains $redefines: ParameterExpression") {
            appendLine("redefine refers parameter: Parameter = $eventPath.${markName(expression.parameter.name)}")
        }
    }

    private fun IndentingStringBuilder.serialize(
        expression: RecordAccessExpression,
        redefines: String,
    ) {
        appendIndent("redefine contains $redefines: ${accessClassName(expression.field)}") {
            serialize(expression.operand, "operand")
        }
    }

    private fun IndentingStringBuilder.serializeVariableReference(
        expression: DirectReferenceExpression,
        redefines: String,
    ) {
        appendIndent("redefine contains $redefines: VariableExpression") {
            appendLine("redefine refers variable: Variable = ${memberReference(expression.declaration)}")
        }
    }

    private fun IndentingStringBuilder.serializeVariableReference(
        expression: VariableReferenceExpression,
        redefines: String,
    ) {
        val path = instancePath(expression) + markName(expression.variable.name)
        appendIndent("redefine contains $redefines: VariableExpression") {
            appendLine("redefine refers variable: Variable = ${path.joinToString(".")}")
        }
    }

    // a region, state or history node named like a statechart member shadows it inside the regions, so the member is reached through the statechart
    private fun memberReference(member: NamedElement): String {
        val statechart = member.eContainer() as? StatechartDeclaration ?: return markName(member.name)
        val shadowed = EcoreUtil2.eAllContents(statechart).asSequence().any {
            (it is Region || it is StateNode) && (it as NamedElement).name == member.name
        }
        if (!shadowed) {
            return markName(member.name)
        }
        return "(statechart as ${qualifiedClassName(statechart)}).${markName(member.name)}"
    }

    // the elements of an asynchronous statechart live in the default adapter's wrapped component
    private fun instancePath(expression: InstanceElementExpression): List<String> {
        val path = mutableListOf(CASE_COMPONENT)
        if (isAsynchronousStatechart(GammaUtils.getTracedComponent(expression))) {
            path += WRAPPED_FEATURE
        }
        for (instance in GammaUtils.getInstancePath(expression)) {
            path += markName(instance.name)
            if (isAsynchronousStatechart(instance.component)) {
                path += WRAPPED_FEATURE
            }
        }
        return path
    }

    private fun isAsynchronousStatechart(component: ComponentDeclaration?): Boolean {
        return component is StatechartDeclaration && component.isAsynchronous
    }

    private fun containmentPath(element: EObject): List<String> {
        val path = mutableListOf<String>()
        var current = element
        while (current !is StatechartDeclaration) {
            path.add(0, markName((current as NamedElement).name))
            current = current.eContainer()
        }
        return path
    }

    private fun serializeInline(expression: Expression): String {
        val node = NodeModelUtils.getNode(expression)
        return NodeModelUtils.getTokenText(node)
    }

    companion object {
        private const val CASE_COMPONENT = "component"
        private const val MILLISECONDS_PER_STEP = 1000
        private const val PARAMETER_CLASS = "parameter"
        private const val VARIABLE_CLASS = "variable"
        private const val ACCESS_CLASS = "access"
        private const val EXECUTION_STEP = "execute"
        private const val INPUT_HALF = "input"
        private const val OUTPUT_HALF = "output"
        private const val WRAPPED_FEATURE = "wrapped"
        private const val ADAPTER_CLASS = "adapter_"
        private const val QUEUE_CLASS = "queue_"
        private const val DISPATCH_CLASS = "dispatch_"
        private const val ENVIRONMENTAL_FLAG = "environmental_"
        private const val IDENTIFIERS = "identifiers"
        private const val IDENTIFIER = "identifier"
        private const val VALUES_ARRAY = "values_"
        private const val VALUE_PARAMETER = "value"
        private const val PORT_REFERENCE = "port_"
        private const val SOURCE_REFERENCE = "source_"
        private const val EVENT_REFERENCE = "event_"
        private const val PARAMETER_REFERENCE = "parameter_"
        private const val CLOCK_REFERENCE = "clock_"
        private const val DISPATCHED_PARAMETER = "parameter"
        private const val SHIFT_TRANSITION = "shift"
        private const val ENQUEUE_TRANSITION = "enqueue_"
    }
}
