/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.conversion;

import com.google.inject.Inject;
import hu.bme.mit.semantifyr.oxsts.lang.naming.NamingUtil;
import org.eclipse.xtext.conversion.IValueConverter;
import org.eclipse.xtext.conversion.ValueConverterException;
import org.eclipse.xtext.nodemodel.INode;

public class QualifiedIdentifierValueConverter implements IValueConverter<String> {
    private static final String DELIMITER = "::";

    @Inject
    private IdentifierValueConverter identifierValueConverter;

    @Override
    public String toValue(String string, INode node) throws ValueConverterException {
        if (string == null) {
            return null;
        }
        var builder = new StringBuilder();
        int index = 0;
        while (index < string.length()) {
            int endIndex = NamingUtil.getEndOfIdentifierSegment(string, index);
            var segment = string.substring(index, endIndex);
            if (!builder.isEmpty()) {
                builder.append(DELIMITER);
            }
            builder.append(identifierValueConverter.toValue(segment, node));
            index = endIndex + DELIMITER.length();
        }
        return builder.toString();
    }

    @Override
    public String toString(String value) throws ValueConverterException {
        var segments = value.split("::", -1);
        var builder = new StringBuilder();
        for (var segment : segments) {
            if (!builder.isEmpty()) {
                builder.append(DELIMITER);
            }
            builder.append(identifierValueConverter.toString(segment));
        }
        return builder.toString();
    }
}
