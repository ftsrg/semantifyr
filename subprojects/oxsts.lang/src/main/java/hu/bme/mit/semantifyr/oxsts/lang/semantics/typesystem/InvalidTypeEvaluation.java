/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.semantics.typesystem;

import hu.bme.mit.semantifyr.oxsts.lang.semantics.expression.RangeEvaluation;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.DomainDeclaration;

public final class InvalidTypeEvaluation implements TypeEvaluation {

    public static InvalidTypeEvaluation Instance = new InvalidTypeEvaluation();

    private InvalidTypeEvaluation() {

    }

    @Override
    public DomainDeclaration getDomain() {
        return null;
    }

    @Override
    public RangeEvaluation getRange() {
        return RangeEvaluation.NONE;
    }

}
