/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.nuxmv.transformation

import com.google.inject.Inject
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Operation

class NuxmvOperationTransformer @Inject constructor(
    private val nuxmvOperationVisitorFactory: NuxmvOperationVisitor.Factory,
) {

    fun transform(
        operation: Operation,
        transitionKind: NuxmvTransitionKind,
        branchTag: String,
        seed: NuxmvBranch = NuxmvBranch(),
    ): NuxmvBranchResult {
        return nuxmvOperationVisitorFactory.create(transitionKind, branchTag, seed).transform(operation)
    }
}
