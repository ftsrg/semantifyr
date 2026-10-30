/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.tests.utils;

import hu.bme.mit.semantifyr.oxsts.model.oxsts.SequenceOperation;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.TransitionDeclaration;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.TransitionKind;
import java.util.List;

public record WrappedTransition(TransitionDeclaration eObject) {

    public String name() {
        return eObject.getName();
    }

    public TransitionKind kind() {
        return eObject.getKind();
    }

    public boolean isAbstract() {
        return eObject.isAbstract();
    }

    public boolean isRedefine() {
        return eObject.isRedefine();
    }

    public List<WrappedParameter> parameters() {
        return eObject.getParameters().stream().map(WrappedParameter::new).toList();
    }

    public List<SequenceOperation> branches() {
        return eObject.getBranches();
    }
}
