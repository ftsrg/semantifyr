/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.conversion;

import com.google.inject.Inject;
import org.eclipse.xtext.common.services.DefaultTerminalConverters;
import org.eclipse.xtext.conversion.IValueConverter;
import org.eclipse.xtext.conversion.ValueConverter;

public class OxstsValueConverterService extends DefaultTerminalConverters {

    @Inject
    private QUOTED_IDValueConverter quotedIdValueConverter;

    @Inject
    private IdentifierValueConverter identifierValueConverter;

    @ValueConverter(rule = "QUOTED_ID")
    public IValueConverter<String> QUOTED_ID() {
        return quotedIdValueConverter;
    }

    @ValueConverter(rule = "Identifier")
    public IValueConverter<String> Identifier() {
        return identifierValueConverter;
    }

}
