/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.tests.utils;

import hu.bme.mit.semantifyr.oxsts.model.oxsts.EnumDeclaration;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.EnumLiteral;
import java.util.List;

public record WrappedEnum(EnumDeclaration eObject) {

    public String name() {
        return eObject.getName();
    }

    public List<EnumLiteral> literals() {
        return eObject.getLiterals();
    }

    public List<String> literalNames() {
        return literals().stream().map(EnumLiteral::getName).toList();
    }

    public EnumLiteral literalByName(String name) {
        return literals().stream()
                .filter(l -> name.equals(l.getName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No literal named '" + name + "' in enum '" + name() + "'"));
    }
}
