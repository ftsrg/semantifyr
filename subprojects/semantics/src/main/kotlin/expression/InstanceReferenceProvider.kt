/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.semantics.expression

import com.google.inject.Singleton
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Instance
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ReferenceExpression
import hu.bme.mit.semantifyr.semantics.utils.OxstsFactory
import hu.bme.mit.semantifyr.semantics.utils.parentSequence

@Singleton
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
