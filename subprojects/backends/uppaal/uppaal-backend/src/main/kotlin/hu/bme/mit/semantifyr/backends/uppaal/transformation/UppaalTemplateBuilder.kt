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

class UppaalTemplateBuilder @Inject constructor(
    private val uppaalOperationTransformer: UppaalOperationTransformer,
) {

    fun build(inlinedOxsts: InlinedOxsts): UppaalTemplate {
        val context = UppaalEmissionContext()
        renderInitTransitions(context, inlinedOxsts.initTransition.branches)
        renderMainTransitions(context, inlinedOxsts.mainTransition.branches)

        val locations = listOf(
            UppaalLocation(UppaalModelTransformer.STARTING_LOCATION_ID, UppaalModelTransformer.STARTING_LOCATION_NAME, UppaalLocationKind.Committed),
            UppaalLocation(UppaalModelTransformer.STABLE_LOCATION_ID, UppaalModelTransformer.STABLE_LOCATION_NAME, UppaalLocationKind.Normal),
        ) + context.locations

        return UppaalTemplate(
            name = UppaalModelTransformer.TEMPLATE_NAME,
            locations = locations,
            initialLocationId = UppaalModelTransformer.STARTING_LOCATION_ID,
            edges = context.edges.toList(),
        )
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
