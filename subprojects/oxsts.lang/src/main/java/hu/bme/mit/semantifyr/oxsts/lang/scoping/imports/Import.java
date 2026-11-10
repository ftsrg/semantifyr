/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.scoping.imports;

import org.eclipse.xtext.naming.QualifiedName;

public record Import(QualifiedName packageName, boolean isImplicit) {

    public static Import implicit(QualifiedName packageName) {
        return new Import(packageName, true);
    }

    public static Import explicit(QualifiedName packageName) {
        return new Import(packageName, false);
    }
}
