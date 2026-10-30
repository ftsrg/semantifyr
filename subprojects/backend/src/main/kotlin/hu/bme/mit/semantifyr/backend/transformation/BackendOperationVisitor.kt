/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backend.transformation

import hu.bme.mit.semantifyr.oxsts.lang.utils.OperationVisitor
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlineCall
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlineChoiceFor
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlineIfOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlineSeqFor

abstract class BackendOperationVisitor<T> : OperationVisitor<T>() {

    override fun visit(operation: InlineCall): T {
        error("Unexpected inline call")
    }

    override fun visit(operation: InlineIfOperation): T {
        error("Unexpected inline-if operation")
    }

    override fun visit(operation: InlineSeqFor): T {
        error("Unexpected inline seq-for operation")
    }

    override fun visit(operation: InlineChoiceFor): T {
        error("Unexpected inline choice-for operation")
    }
}
