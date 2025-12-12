/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.scoping.imports;

import org.eclipse.emf.common.util.URI;

public record Import(URI uri, boolean isImplicit) {

    public static Import implicit(URI importedPackage) {
        return new Import(importedPackage, true);
    }

    public static Import explicit(URI uri) {
        return new Import(uri,false);
    }

}
