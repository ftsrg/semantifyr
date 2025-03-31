/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.naming;

import com.google.common.base.Function;
import com.google.inject.Singleton;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.BaseType;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.NamedElement;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Transition;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.xtext.EcoreUtil2;

@Singleton
public class OxstsNameProvider implements Function<EObject, String> {

    @Override
    public String apply(EObject input) {
        return getName(input);
    }

    protected String getName(EObject element) {
        return switch (element) {
            case Transition transition -> getTransitionName(transition);
            case NamedElement namedElement -> namedElement.getName();
            default -> null;
        };
    }

    protected String getTransitionName(Transition transition) {
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
