/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.uppaal.transformation

import com.google.inject.Inject
import hu.bme.mit.semantifyr.backends.uppaal.ir.UppaalEdge
import hu.bme.mit.semantifyr.backends.uppaal.ir.UppaalLocation
import hu.bme.mit.semantifyr.backends.uppaal.ir.UppaalLocationKind
import hu.bme.mit.semantifyr.backends.uppaal.ir.UppaalTemplate
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlinedOxsts
import hu.bme.mit.semantifyr.oxsts.model.oxsts.SequenceOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.VariableDeclaration

class UppaalTemplateBuilder @Inject constructor(
    private val uppaalOperationTransformer: UppaalOperationTransformer,
    private val uppaalVariableTransformer: UppaalVariableTransformer,
) {

    fun build(inlinedOxsts: InlinedOxsts): UppaalTemplate {
        val context = UppaalEmissionContext()
        val uninitialized = inlinedOxsts.variables.filter {
            it.expression == null && !uppaalVariableTransformer.isClock(it)
        }
        renderInitialHavoc(context, uninitialized)
        renderInitTransitions(context, inlinedOxsts.initTransition.branches)
        renderMainTransitions(context, inlinedOxsts.mainTransition.branches)

        val initial = if (uninitialized.isEmpty()) {
            null
        } else {
            UppaalLocation(UppaalModelTransformer.INITIAL_LOCATION_ID, UppaalModelTransformer.INITIAL_LOCATION_NAME, UppaalLocationKind.Committed)
        }
        val start = UppaalLocation(UppaalModelTransformer.STARTING_LOCATION_ID, UppaalModelTransformer.STARTING_LOCATION_NAME, UppaalLocationKind.Committed)
        val running = UppaalLocation(UppaalModelTransformer.STABLE_LOCATION_ID, UppaalModelTransformer.STABLE_LOCATION_NAME, UppaalLocationKind.Normal)

        return UppaalTemplate(
            name = UppaalModelTransformer.TEMPLATE_NAME,
            locations = listOfNotNull(initial, start, running) + context.locations,
            initialLocationId = (initial ?: start).id,
            edges = context.edges.toList(),
        )
    }

    // the havoc runs before Start, so the witness keeps reading the initial state at Start
    private fun renderInitialHavoc(
        context: UppaalEmissionContext,
        variables: List<VariableDeclaration>,
    ) {
        var sourceId = UppaalModelTransformer.INITIAL_LOCATION_ID
        for ((index, variable) in variables.withIndex()) {
            val targetId = if (index == variables.lastIndex) {
                UppaalModelTransformer.STARTING_LOCATION_ID
            } else {
                context.freshCommitted("havoc").id
            }
            uppaalOperationTransformer.havoc(context, sourceId, targetId, variable)
            sourceId = targetId
        }
    }

    private fun renderInitTransitions(
        context: UppaalEmissionContext,
        branches: List<SequenceOperation>,
    ) {
        if (branches.isEmpty()) {
            // No init behaviour: the model immediately reaches the stable state.
            context.edges += UppaalEdge(
                UppaalModelTransformer.STARTING_LOCATION_ID,
                UppaalModelTransformer.STABLE_LOCATION_ID,
            )
            return
        }
        for (branch in branches) {
            uppaalOperationTransformer.transform(
                context,
                UppaalModelTransformer.STARTING_LOCATION_ID,
                UppaalModelTransformer.STABLE_LOCATION_ID,
                branch,
            )
        }
    }

    private fun renderMainTransitions(
        context: UppaalEmissionContext,
        branches: List<SequenceOperation>,
    ) {
        for (branch in branches) {
            uppaalOperationTransformer.transform(
                context,
                UppaalModelTransformer.STABLE_LOCATION_ID,
                UppaalModelTransformer.STABLE_LOCATION_ID,
                branch,
            )
        }
    }
}
