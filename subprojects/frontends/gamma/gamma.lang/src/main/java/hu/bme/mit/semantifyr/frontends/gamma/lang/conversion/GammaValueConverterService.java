/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.frontends.gamma.lang.conversion;

import com.google.inject.Inject;
import org.eclipse.xtext.common.services.DefaultTerminalConverters;
import org.eclipse.xtext.conversion.IValueConverter;
import org.eclipse.xtext.conversion.ValueConverter;

public class GammaValueConverterService extends DefaultTerminalConverters {

    @Inject
    private GammaQualifiedNameValueConverter qualifiedNameValueConverter;

    @ValueConverter(rule = "QualifiedName")
    public IValueConverter<String> QualifiedName() {
        return qualifiedNameValueConverter;
    }
}
