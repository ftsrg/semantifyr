/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.frontends.gamma.lang.naming;

import org.eclipse.xtext.naming.IQualifiedNameConverter;

public class GammaQualifiedNameConverter extends IQualifiedNameConverter.DefaultImpl {

    @Override
    public String getDelimiter() {
        return "::";
    }
}
