/*
 * SPDX-FileCopyrightText: 2023-2024 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang;

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

    public Class<? extends IQualifiedNameProvider> bindIQualifiedNameProvider() {
        return OxstsQualifiedNameProvider.class;
    }

}

class OxstsQualifiedNameConverter extends IQualifiedNameConverter.DefaultImpl {
    @Override
    public String getDelimiter() {
        return "::";
    }
}
