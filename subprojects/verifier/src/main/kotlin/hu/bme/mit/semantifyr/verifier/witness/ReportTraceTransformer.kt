/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.verifier.witness

import com.google.inject.Inject
import hu.bme.mit.semantifyr.compiler.pipeline.context.ArraySlot
import hu.bme.mit.semantifyr.compiler.pipeline.context.FlattenedCompilationContext
import hu.bme.mit.semantifyr.compiler.pipeline.inlining.ArgumentTrace
import hu.bme.mit.semantifyr.compiler.pipeline.inlining.TransitionCallTrace
import hu.bme.mit.semantifyr.compiler.pipeline.utils.eAllOfType
import hu.bme.mit.semantifyr.oxsts.lang.naming.OxstsQualifiedNameProvider
import hu.bme.mit.semantifyr.oxsts.model.oxsts.AssignmentOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ElementReference
import hu.bme.mit.semantifyr.oxsts.model.oxsts.LiteralBoolean
import hu.bme.mit.semantifyr.oxsts.model.oxsts.NamedElement
import hu.bme.mit.semantifyr.oxsts.model.oxsts.OxstsModelPackage
import hu.bme.mit.semantifyr.oxsts.model.oxsts.VariableDeclaration
import hu.bme.mit.semantifyr.verifier.ObservedParameter
import hu.bme.mit.semantifyr.verifier.ObservedSlot
import hu.bme.mit.semantifyr.verifier.ObservedTransition
import hu.bme.mit.semantifyr.verifier.ObservedVariable
import hu.bme.mit.semantifyr.verifier.ReportCall
import hu.bme.mit.semantifyr.verifier.ReportInitialEntry
import hu.bme.mit.semantifyr.verifier.ReportState
import hu.bme.mit.semantifyr.verifier.ReportStep
import hu.bme.mit.semantifyr.verifier.ReportTrace
import hu.bme.mit.semantifyr.verifier.ReportType
import hu.bme.mit.semantifyr.verifier.ReportValue

class ReportTraceTransformer @Inject constructor(
    private val oxstsQualifiedNameProvider: OxstsQualifiedNameProvider,
    private val reportValueConverter: ReportValueConverter,
) {

    private class ReportEntry(
        val key: String,
        val type: ReportType,
        val value: ReportValue,
        val slot: ObservedSlot? = null,
    )

    fun transform(classWitness: ClassWitness, compilation: FlattenedCompilationContext): ReportTrace {
        val siteOrder = siteOrder(compilation)
        val path = listOf(classWitness.initializedState) + classWitness.transitionStates
        val states = path.distinct()
        val entries = states.map {
            entries(it)
        }
        val variables = linkedMapOf<String, ObservedVariable>()
        for (stateEntries in entries) {
            for (entry in stateEntries) {
                variables.putIfAbsent(entry.key, ObservedVariable(entry.key, entry.type, entry.slot))
            }
        }
        return ReportTrace(
            root = rootName(compilation),
            transitions = observedTransitions(compilation, siteOrder),
            variables = variables.values.toList(),
            initial = ReportInitialEntry(
                state = 0,
                calls = calls(classWitness.initializedState, compilation, siteOrder),
            ),
            states = entries.map { stateEntries ->
                ReportState(
                    stateEntries.associate {
                        it.key to it.value
                    },
                )
            },
            steps = path.zipWithNext { source, target ->
                ReportStep(
                    source = states.indexOf(source),
                    target = states.indexOf(target),
                    elapsed = target.elapsed,
                    calls = calls(target, compilation, siteOrder),
                )
            },
        )
    }

    private fun rootName(compilation: FlattenedCompilationContext): String {
        val root = compilation.rootInstance.children.map {
            it.domain
        }.singleOrNull {
            it.eContainer() !is OxstsModelPackage
        } as? NamedElement ?: error("the inlined model has no single root instance")
        return root.name
    }

    private fun siteOrder(compilation: FlattenedCompilationContext): Map<VariableDeclaration, Int> {
        val order = linkedMapOf<VariableDeclaration, Int>()
        for (assignment in compilation.inlinedOxsts.eAllOfType<AssignmentOperation>()) {
            val variable = (assignment.reference as? ElementReference)?.element as? VariableDeclaration ?: continue
            val value = assignment.expression
            if (variable in compilation.transitionCallTraces.tracerVariables && value is LiteralBoolean && value.isValue) {
                order.putIfAbsent(variable, order.size)
            }
        }
        return order
    }

    private fun observedTransitions(compilation: FlattenedCompilationContext, siteOrder: Map<VariableDeclaration, Int>): List<ObservedTransition> {
        val traceVariables = compilation.inlinedOxsts.variables.filter {
            it in compilation.transitionCallTraces.tracerVariables
        }.sortedBy {
            siteOrder[it] ?: Int.MAX_VALUE
        }
        return traceVariables.map {
            compilation.transitionCallTraces.getTransitionCallTrace(it).transitionDeclaration
        }.distinct().map { transition ->
            ObservedTransition(
                transition = oxstsQualifiedNameProvider.getFullyQualifiedNameString(transition),
                parameters = transition.parameters.map {
                    ObservedParameter(it.name, reportValueConverter.type(it.typeSpecification))
                },
            )
        }
    }

    private fun calls(
        state: ClassWitnessState,
        compilation: FlattenedCompilationContext,
        siteOrder: Map<VariableDeclaration, Int>,
    ): List<ReportCall> {
        val sites = state.activatedTraces.map {
            it.variable
        }.sortedBy {
            siteOrder[it] ?: Int.MAX_VALUE
        }.map {
            compilation.transitionCallTraces.getTransitionCallTrace(it)
        }
        val activated = sites.toSet()
        val childrenByParent = sites.groupBy {
            it.parent?.takeIf { parent ->
                parent in activated
            }
        }

        fun build(site: TransitionCallTrace): ReportCall {
            return ReportCall(
                self = reportValueConverter.path(site.self),
                transition = oxstsQualifiedNameProvider.getFullyQualifiedNameString(site.transitionDeclaration),
                arguments = arguments(site, state),
                calls = childrenByParent[site].orEmpty().map {
                    build(it)
                },
            )
        }

        return childrenByParent[null].orEmpty().map {
            build(it)
        }
    }

    private fun arguments(site: TransitionCallTrace, state: ClassWitnessState): Map<String, ReportValue> {
        return site.arguments.associate {
            val value = when (it) {
                is ArgumentTrace.Constant -> reportValueConverter.value(it.evaluation)
                is ArgumentTrace.Recorded -> reportValueConverter.value(
                    state.recordedArguments[it.variable] ?: error("the recorded argument ${it.variable.name} has no value"),
                )
            }
            it.parameterDeclaration.name to value
        }
    }

    private fun entries(state: ClassWitnessState): List<ReportEntry> {
        val groups = linkedMapOf<String, EntryGroup>()
        for (stateValue in state.values) {
            val variable = stateValue.variable
            val recordField = variable.recordField
            when {
                recordField != null -> {
                    val group = groups.getOrPut(reportValueConverter.path(variable.reference)) {
                        EntryGroup.Record(
                            reportValueConverter.elementType(variable.declaredVariable.typeSpecification),
                            variable.arraySlot?.let {
                                observedSlot(variable, it)
                            },
                        )
                    } as EntryGroup.Record
                    val fields = recordField.fields.dropLast(1).fold(group.fields) { fields, field ->
                        fields.nested.getOrPut(field.name) {
                            RecordFields()
                        }
                    }
                    fields.values[recordField.fields.last().name] = reportValueConverter.value(stateValue.value)
                }
                variable.arraySlot != null -> {
                    val key = reportValueConverter.path(variable.reference)
                    groups[key] = EntryGroup.Single(
                        ReportEntry(key, reportValueConverter.elementType(variable.declaredVariable.typeSpecification), reportValueConverter.value(stateValue.value), observedSlot(variable, variable.arraySlot)),
                    )
                }
                else -> {
                    val key = reportValueConverter.path(variable.reference)
                    groups[key] = EntryGroup.Single(
                        ReportEntry(key, reportValueConverter.type(variable.declaredVariable.typeSpecification), reportValueConverter.value(stateValue.value)),
                    )
                }
            }
        }
        for (clockValue in state.clockValues) {
            val key = reportValueConverter.path(clockValue.variable.reference)
            groups[key] = EntryGroup.Single(
                ReportEntry(key, reportValueConverter.type(clockValue.variable.declaredVariable.typeSpecification), reportValueConverter.value(clockValue.value)),
            )
        }
        return groups.map { (key, group) ->
            when (group) {
                is EntryGroup.Single -> group.entry
                is EntryGroup.Record -> ReportEntry(key, group.type, group.fields.toValue(), group.slot)
            }
        }
    }

    private fun observedSlot(variable: ClassWitnessVariable, arraySlot: ArraySlot): ObservedSlot {
        return ObservedSlot(
            array = reportValueConverter.path(variable.declaredReference),
            index = arraySlot.index,
            size = arraySlot.size,
        )
    }

    private class RecordFields {
        val values = linkedMapOf<String, ReportValue>()
        val nested = linkedMapOf<String, RecordFields>()

        fun toValue(): ReportValue {
            return ReportValue.Record(
                values + nested.mapValues {
                    it.value.toValue()
                },
            )
        }
    }

    private sealed interface EntryGroup {
        class Single(val entry: ReportEntry) : EntryGroup

        class Record(val type: ReportType, val slot: ObservedSlot?) : EntryGroup {
            val fields = RecordFields()
        }
    }
}
