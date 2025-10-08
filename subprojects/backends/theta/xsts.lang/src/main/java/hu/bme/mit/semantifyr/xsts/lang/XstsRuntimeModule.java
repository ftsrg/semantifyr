/*
 * SPDX-FileCopyrightText: 2023-2024 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.xsts.lang;


import com.google.inject.Binder;
import com.google.inject.name.Names;
import hu.bme.mit.semantifyr.xsts.lang.resource.XstsResourceDescriptionManager;
import hu.bme.mit.semantifyr.xsts.lang.scoping.EmptyGlobalScopeProvider;
import hu.bme.mit.semantifyr.xsts.lang.scoping.XstsLocalScopeProvider;
import org.eclipse.xtext.resource.IResourceDescription;
import org.eclipse.xtext.scoping.IGlobalScopeProvider;
import org.eclipse.xtext.scoping.IScopeProvider;
import org.eclipse.xtext.scoping.impl.AbstractDeclarativeScopeProvider;

/**
 * Use this class to register components to be used at runtime / without the Equinox extension registry.
 */
public class XstsRuntimeModule extends AbstractXstsRuntimeModule {

    @Override
    public Class<? extends IGlobalScopeProvider> bindIGlobalScopeProvider() {
        return EmptyGlobalScopeProvider.class;
    }

    @Override
    public void configureIScopeProviderDelegate(Binder binder) {
        binder.bind(IScopeProvider.class).annotatedWith(Names.named(AbstractDeclarativeScopeProvider.NAMED_DELEGATE))
                .to(XstsLocalScopeProvider.class);
    }


    // Method name follows Xtext convention.
    @SuppressWarnings("squid:S100")
    public Class<? extends IResourceDescription.Manager> bindIResourceDescription$Manager() {
        return XstsResourceDescriptionManager.class;
    }

}
