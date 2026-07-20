/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.tests.utils;

import hu.bme.mit.semantifyr.oxsts.model.oxsts.DataTypeDeclaration;

public record WrappedDataType(DataTypeDeclaration eObject) {

    public String name() {
        return eObject.getName();
    }
}
