/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.frontends.gamma.lang;


import com.google.inject.Binder;
import com.google.inject.name.Names;
import hu.bme.mit.semantifyr.frontends.gamma.lang.scoping.EmptyGlobalScopeProvider;
import hu.bme.mit.semantifyr.frontends.gamma.lang.scoping.GammaLocalScopeProvider;
import org.eclipse.xtext.scoping.IGlobalScopeProvider;
import org.eclipse.xtext.scoping.IScopeProvider;
import org.eclipse.xtext.scoping.impl.AbstractDeclarativeScopeProvider;

/**
 * Use this class to register components to be used at runtime / without the Equinox extension registry.
 */
public class GammaRuntimeModule extends AbstractGammaRuntimeModule {

    @Override
    public Class<? extends IGlobalScopeProvider> bindIGlobalScopeProvider() {
        return EmptyGlobalScopeProvider.class;
    }

    @Override
    public void configureIScopeProviderDelegate(Binder binder) {
        binder.bind(IScopeProvider.class).annotatedWith(Names.named(AbstractDeclarativeScopeProvider.NAMED_DELEGATE))
                .to(GammaLocalScopeProvider.class);
    }

}
