/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.naming;

import hu.bme.mit.semantifyr.oxsts.model.oxsts.NamedElement;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.TransitionDeclaration;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.xtext.util.Strings;

public class NamingUtil {

    public static String getName(EObject element) {
        return switch (element) {
            case TransitionDeclaration transition -> getTransitionName(transition);
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

}
