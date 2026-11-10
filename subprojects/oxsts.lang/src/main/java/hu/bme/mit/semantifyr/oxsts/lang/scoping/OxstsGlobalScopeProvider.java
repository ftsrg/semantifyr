/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.scoping;

import com.google.common.base.Predicate;
import com.google.inject.Inject;
import hu.bme.mit.semantifyr.oxsts.lang.library.LibraryAdapterFinder;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.xtext.resource.IEObjectDescription;
import org.eclipse.xtext.scoping.IScope;
import org.eclipse.xtext.scoping.impl.ResourceSetGlobalScopeProvider;

public class OxstsGlobalScopeProvider extends ResourceSetGlobalScopeProvider {

    @Inject
    private LibraryAdapterFinder libraryAdapterFinder;

    @Override
    public IScope getScope(Resource resource, EReference reference, Predicate<IEObjectDescription> filter) {
        libraryAdapterFinder.getOrInstall(resource); // ensure Library resources are loaded

        return super.getScope(resource, reference, filter);
    }
}
