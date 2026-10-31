/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.uppaal.transformation

import com.google.inject.Inject
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Operation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.VariableDeclaration

class UppaalOperationTransformer @Inject constructor(
    private val uppaalOperationVisitorFactory: UppaalOperationVisitor.Factory,
) {

    fun transform(
        context: UppaalEmissionContext,
        sourceId: String,
        targetId: String,
        operation: Operation,
    ) {
        uppaalOperationVisitorFactory.create(context, sourceId, targetId).transform(operation)
    }

    fun havoc(
        context: UppaalEmissionContext,
        sourceId: String,
        targetId: String,
        variable: VariableDeclaration,
    ) {
        uppaalOperationVisitorFactory.create(context, sourceId, targetId).havoc(variable)
    }
}
