/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.semantics.typesystem;

import hu.bme.mit.semantifyr.oxsts.model.oxsts.DomainDeclaration;

public sealed interface TypeEvaluation permits ImmutableTypeEvaluation, InvalidTypeEvaluation {

    InvalidTypeEvaluation INVALID = new InvalidTypeEvaluation();

    default DomainDeclaration getDomain() {
        return null;
    }

}
