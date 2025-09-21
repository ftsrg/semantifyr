/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.semantics.transformation.instantiation

import com.google.inject.Inject
import com.google.inject.Singleton
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlinedOxsts
import hu.bme.mit.semantifyr.semantics.transformation.constraints.ConstraintChecker

@Singleton
class OxstsInflator {

    @Inject
    lateinit var oxstsClassInstantiator: OxstsClassInstantiator

    @Inject
    private lateinit var constraintChecker: ConstraintChecker

    fun inflateInstanceModel(inlinedOxsts: InlinedOxsts) {
        oxstsClassInstantiator.instantiateModel(inlinedOxsts.instanceModel)

        constraintChecker.checkConstraints(inlinedOxsts.instanceModel)
    }

    fun deflateInstanceModel(inlinedOxsts: InlinedOxsts) {
        // TODO: place variables to inlineModel -> rewrite expressions to new variables
        // TODO: transform pointers to integer ids

//        logger.info("Rewriting operations")
//
//        xsts.rewriteReferences(instanceModel)
    }

}
