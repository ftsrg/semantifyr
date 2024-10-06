/*
 * SPDX-FileCopyrightText: 2023-2024 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.xsts.lang.scoping;


import hu.bme.mit.semantifyr.oxsts.model.oxsts.*;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.xtext.EcoreUtil2;
import org.eclipse.xtext.scoping.IScope;
import org.eclipse.xtext.scoping.Scopes;

import java.util.ArrayList;

/**
 * This class contains custom scoping description.
 * 
 * See https://www.eclipse.org/Xtext/documentation/303_runtime_concepts.html#scoping
 * on how and when to use it.
 */
public class XstsScopeProvider extends AbstractXstsScopeProvider {

    @Override
    public IScope getScope(EObject context, EReference reference) {
        if (context instanceof DeclarationReferenceExpression expression) {
            var accessibleElements = new ArrayList<EObject>();
            var xsts = EcoreUtil2.getContainerOfType(context, XSTS.class);
            var enums = xsts.getEnums();
            accessibleElements.addAll(enums);
            accessibleElements.addAll(enums.stream().flatMap((e) -> e.getLiterals().stream()).toList());
            accessibleElements.addAll(xsts.getVariables());

            return Scopes.scopeFor(accessibleElements, super.getScope(context, reference));
        }

        return super.getScope(context, reference);
    }

}
