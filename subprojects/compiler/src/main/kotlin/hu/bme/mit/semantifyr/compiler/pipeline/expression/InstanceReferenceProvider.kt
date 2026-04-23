/*
 * SPDX-FileCopyrightText: 2025-2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler.pipeline.expression

import hu.bme.mit.semantifyr.compiler.pipeline.instantiation.Instance
import hu.bme.mit.semantifyr.compiler.pipeline.utils.OxstsFactory
import hu.bme.mit.semantifyr.compiler.pipeline.utils.parentSequence
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ReferenceExpression

class InstanceReferenceProvider {

    fun getReference(instance: Instance): ReferenceExpression {
        if (instance.parent == null) {
            // root instance -> self from the root context
            return OxstsFactory.createSelfReference()
        }

        val containmentTree = instance.parentSequence().toList().asReversed().asSequence().drop(1).iterator()

        var reference: ReferenceExpression = OxstsFactory.createElementReference(containmentTree.next().domain)

        while (containmentTree.hasNext()) {
            reference = OxstsFactory.createNavigationSuffixExpression().also {
                it.primary = reference
                it.member = containmentTree.next().domain
            }
        }

        return reference
    }

}
