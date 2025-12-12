/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.naming;

import hu.bme.mit.semantifyr.oxsts.model.oxsts.NamedElement;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.PropertyDeclaration;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.TransitionDeclaration;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.xtext.util.Strings;

import java.util.regex.Pattern;

public class NamingUtil {

    public static final String ID_REGEX_STRING = "[a-zA-Z_]\\w*";
    private static final Pattern ID_REGEX = Pattern.compile(ID_REGEX_STRING);

    public static String getName(EObject element) {
        return switch (element) {
            case TransitionDeclaration transition -> getTransitionName(transition);
            case PropertyDeclaration property -> getPropertyName(property);
            case NamedElement namedElement -> namedElement.getName();
            default -> null;
        };
    }

    protected static String getTransitionName(TransitionDeclaration transition) {
        if (Strings.isEmpty(transition.getName())) {
            return switch (transition.getKind()) {
                case TRAN -> "main";
                case ENV -> "env";
                case INIT -> "init";
                case HAVOC -> "havoc";
            };
        }

        return transition.getName();
    }

    protected static String getPropertyName(PropertyDeclaration property) {
        if (Strings.isEmpty(property.getName())) {
            return "prop";
        }

        return property.getName();
    }

    public static int getEndOfIdentifierSegment(String input, int startIndex) {
        if (isQuotedId(input, startIndex)) {
            return getEndOfQuotedIdSegment(input, startIndex);
        }

        return getEndOfSimpleIdSegment(input, startIndex);
    }

    private static int getEndOfQuotedIdSegment(String input, int startIndex) {
        var lastQuote = input.indexOf('\'', startIndex + 1);
        if (lastQuote < 0) {
            throw new IllegalArgumentException("ID is not a valid quoted identifier");
        }

        return lastQuote + 1;
    }

    private static int getEndOfSimpleIdSegment(String input, int startIndex) {
        var end = input.indexOf(':', startIndex + 1);
        if (end >= 0) {
            return end;
        }

        // this is the last segment
        return input.length();
    }

    public static boolean isQuotedId(String name) {
        return name != null && ! name.isBlank() && name.charAt(0) == '\'';
    }

    public static boolean isQuotedId(String name, int startIndex) {
        return name != null && name.length() > startIndex && name.charAt(startIndex) == '\'';
    }

    public static boolean isSimpleId(String name) {
        return name != null && ID_REGEX.matcher(name).matches();
    }

}
