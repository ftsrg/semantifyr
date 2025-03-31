/*
 * SPDX-FileCopyrightText: 2023-2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang;

import hu.bme.mit.semantifyr.oxsts.lang.conversion.OxstsValueConverterService;
import hu.bme.mit.semantifyr.oxsts.lang.naming.OxstsQualifiedNameConverter;
import hu.bme.mit.semantifyr.oxsts.lang.naming.OxstsQualifiedNameProvider;
import org.eclipse.xtext.conversion.IValueConverterService;
import org.eclipse.xtext.naming.IQualifiedNameConverter;
import org.eclipse.xtext.naming.IQualifiedNameProvider;

public class OxstsRuntimeModule extends AbstractOxstsRuntimeModule {

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

}
