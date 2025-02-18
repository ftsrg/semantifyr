/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.scoping;

import org.eclipse.xtext.naming.IQualifiedNameConverter;

public class OxstsQualifiedNameConverter extends IQualifiedNameConverter.DefaultImpl {
    @Override
    public String getDelimiter() {
        return "::";
    }
}
