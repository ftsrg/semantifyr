/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.frontends.gamma.lang.scoping;

import com.google.common.base.Predicate;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.xtext.resource.IEObjectDescription;
import org.eclipse.xtext.scoping.IGlobalScopeProvider;
import org.eclipse.xtext.scoping.IScope;

public class EmptyGlobalScopeProvider implements IGlobalScopeProvider {

    @Override
    public IScope getScope(Resource context, EReference reference, Predicate<IEObjectDescription> filter) {
        return IScope.NULLSCOPE;
    }

}
