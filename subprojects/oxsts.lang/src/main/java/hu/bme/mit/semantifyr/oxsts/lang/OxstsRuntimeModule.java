/*
 * SPDX-FileCopyrightText: 2023-2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang;

import com.google.inject.Binder;
import com.google.inject.name.Names;
import hu.bme.mit.semantifyr.oxsts.lang.conversion.OxstsValueConverterService;
import hu.bme.mit.semantifyr.oxsts.lang.naming.OxstsQualifiedNameConverter;
import hu.bme.mit.semantifyr.oxsts.lang.naming.OxstsQualifiedNameProvider;
import hu.bme.mit.semantifyr.oxsts.lang.resource.OxstsResourceDescriptionManager;
import hu.bme.mit.semantifyr.oxsts.lang.scoping.OxstsGlobalScopeProvider;
import hu.bme.mit.semantifyr.oxsts.lang.scoping.OxstsLocalScopeProvider;
import org.eclipse.xtext.conversion.IValueConverterService;
import org.eclipse.xtext.naming.IQualifiedNameConverter;
import org.eclipse.xtext.naming.IQualifiedNameProvider;
import org.eclipse.xtext.resource.IResourceDescription;
import org.eclipse.xtext.scoping.IGlobalScopeProvider;
import org.eclipse.xtext.scoping.IScopeProvider;
import org.eclipse.xtext.scoping.impl.AbstractDeclarativeScopeProvider;

public class OxstsRuntimeModule extends AbstractOxstsRuntimeModule {

    @SuppressWarnings("unused")
    public Class<? extends IQualifiedNameConverter> bindIQualifiedNameConverter() {
        return OxstsQualifiedNameConverter.class;
    }

    @Override
    public Class<? extends IQualifiedNameProvider> bindIQualifiedNameProvider() {
        return OxstsQualifiedNameProvider.class;
    }

    @Override
    public Class<? extends IValueConverterService> bindIValueConverterService() {
        return OxstsValueConverterService.class;
    }

    @SuppressWarnings("unused")
    public Class<? extends IResourceDescription.Manager> bindIResourceDescription$Manager() {
        return OxstsResourceDescriptionManager.class;
    }

    @Override
    public Class<? extends IGlobalScopeProvider> bindIGlobalScopeProvider() {
        return OxstsGlobalScopeProvider.class;
    }

    @Override
    public void configureIScopeProviderDelegate(Binder binder) {
        binder.bind(IScopeProvider.class).annotatedWith(Names.named(AbstractDeclarativeScopeProvider.NAMED_DELEGATE))
                .to(OxstsLocalScopeProvider.class);
    }

}
