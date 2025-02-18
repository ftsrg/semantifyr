/*
 * SPDX-FileCopyrightText: 2023-2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang;

import hu.bme.mit.semantifyr.oxsts.lang.scoping.OxstsQualifiedNameConverter;
import hu.bme.mit.semantifyr.oxsts.lang.scoping.OxstsQualifiedNameProvider;
import org.eclipse.xtext.naming.IQualifiedNameConverter;
import org.eclipse.xtext.naming.IQualifiedNameProvider;

/**
 * Use this class to register components to be used at runtime / without the Equinox extension registry.
 */
public class OxstsRuntimeModule extends AbstractOxstsRuntimeModule {

    public Class<? extends IQualifiedNameConverter> bindIQualifiedNameConverter() {
        return OxstsQualifiedNameConverter.class;
    }

    @Override
    public Class<? extends IQualifiedNameProvider> bindIQualifiedNameProvider() {
        return OxstsQualifiedNameProvider.class;
    }

}
