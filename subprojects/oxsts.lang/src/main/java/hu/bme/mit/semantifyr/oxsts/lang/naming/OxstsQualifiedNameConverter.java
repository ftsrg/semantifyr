/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.naming;

import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import hu.bme.mit.semantifyr.oxsts.lang.conversion.IdentifierValueConverter;
import jakarta.inject.Singleton;
import org.eclipse.xtext.naming.IQualifiedNameConverter;
import org.eclipse.xtext.naming.QualifiedName;

import java.util.ArrayList;

@Singleton
public class OxstsQualifiedNameConverter implements IQualifiedNameConverter {

    @Inject
    private IdentifierValueConverter valueConverter;

    public String getDelimiter() {
        return "::";
    }

    @Override
    public String toString(QualifiedName qualifiedName) {
        if (qualifiedName == null) {
            throw new IllegalArgumentException("Qualified name cannot be null");
        }
        var builder = new StringBuilder();
        for (int i = 0; i < qualifiedName.getSegmentCount(); i++) {
            if (i > 0) {
                builder.append(getDelimiter());
            }
            var segment = qualifiedName.getSegment(i);
            builder.append(valueConverter.toString(segment));
        }
        return builder.toString();
    }

    @Override
    public QualifiedName toQualifiedName(String qualifiedNameAsString) {
        Preconditions.checkArgument(qualifiedNameAsString != null, "Qualified name cannot be null");
        Preconditions.checkArgument(!qualifiedNameAsString.isEmpty(), "Qualified name cannot be empty");
        var segments = new ArrayList<String>();
        int length = qualifiedNameAsString.length();
        int delimiterLength = getDelimiter().length();
        int index = 0;
        while (index < length) {
            int endIndex = NamingUtil.getEndOfIdentifierSegment(qualifiedNameAsString, index);
            if (endIndex <= 0) {
                throw new IllegalArgumentException("Invalid qualified name: " + qualifiedNameAsString);
            }
            var segment = qualifiedNameAsString.substring(index, endIndex);
            segments.add(valueConverter.toValue(segment, null));
            index = endIndex + delimiterLength;
        }
        return QualifiedName.create(segments);
    }

}
