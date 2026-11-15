/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.frontends.gamma.lang.conversion;

import org.eclipse.xtext.conversion.impl.QualifiedNameValueConverter;

// a segment of a package name may be escaped with ^ like any other identifier, as in Gamma
public class GammaQualifiedNameValueConverter extends QualifiedNameValueConverter {

    @Override
    protected String getStringNamespaceDelimiter() {
        return "::";
    }

    @Override
    protected String getValueNamespaceDelimiter() {
        return "::";
    }
}
