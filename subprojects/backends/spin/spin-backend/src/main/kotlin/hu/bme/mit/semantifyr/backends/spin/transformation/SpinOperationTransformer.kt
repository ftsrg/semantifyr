/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.spin.transformation

import com.google.inject.Inject
import hu.bme.mit.semantifyr.backend.text.IndentingBuilder
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Operation

class SpinOperationTransformer @Inject constructor(
    private val spinOperationVisitorFactory: SpinOperationVisitor.Factory,
) {

    fun transform(operation: Operation, builder: IndentingBuilder) {
        spinOperationVisitorFactory.create(builder).transform(operation)
    }
}
