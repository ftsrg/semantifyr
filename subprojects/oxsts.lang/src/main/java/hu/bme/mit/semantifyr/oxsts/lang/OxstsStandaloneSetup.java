/*
 * SPDX-FileCopyrightText: 2023-2024 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang;

import com.google.inject.Guice;
import com.google.inject.Injector;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.OxstsPackage;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.xtext.resource.IResourceServiceProvider;

/** Initialization support for running Xtext languages without Equinox extension registry. */
public class OxstsStandaloneSetup extends OxstsStandaloneSetupGenerated {

    private static final String EXTENSION = "oxsts";

    @Override
    public Injector createInjectorAndDoEMFRegistration() {
        if (!EPackage.Registry.INSTANCE.containsKey(OxstsPackage.eNS_URI)) {
            EPackage.Registry.INSTANCE.put(OxstsPackage.eNS_URI, OxstsPackage.eINSTANCE);
        }
        return super.createInjectorAndDoEMFRegistration();
    }

    public Injector createInjectorWithoutGlobalRegistration() {
        OxstsPackage.eINSTANCE.eClass();
        return Guice.createInjector(new OxstsRuntimeModule());
    }

    @Override
    public void register(Injector injector) {
        var resourceFactoryMap = Resource.Factory.Registry.INSTANCE.getExtensionToFactoryMap();
        var serviceProviderMap = IResourceServiceProvider.Registry.INSTANCE.getExtensionToFactoryMap();
        if (resourceFactoryMap.containsKey(EXTENSION) && serviceProviderMap.containsKey(EXTENSION)) {
            return;
        }
        super.register(injector);
    }

    public static void doSetup() {
        new OxstsStandaloneSetup().createInjectorAndDoEMFRegistration();
    }
}
