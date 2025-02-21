/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.naming;

import hu.bme.mit.semantifyr.oxsts.model.oxsts.BaseType;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Transition;
import jakarta.inject.Singleton;
import org.eclipse.xtext.EcoreUtil2;
import org.eclipse.xtext.naming.DefaultDeclarativeQualifiedNameProvider;
import org.eclipse.xtext.naming.QualifiedName;

@Singleton
public class OxstsQualifiedNameProvider extends DefaultDeclarativeQualifiedNameProvider {

    protected QualifiedName qualifiedName(Transition transition) {
        var parentsName = getFullyQualifiedName(transition.eContainer());
        var name = transitionWithImplicitName(transition);

        return parentsName.append(name);
    }

    public static String transitionWithImplicitName(Transition transition) {
        var baseType = EcoreUtil2.getContainerOfType(transition, BaseType.class);

        if (baseType.getMainTransition().contains(transition)) {
            return "main";
        } else if (baseType.getInitTransition().contains(transition)) {
            return "init";
        } else if (baseType.getHavocTransition().contains(transition)) {
            return "havoc";
        }

        return transition.getName();
    }

}
