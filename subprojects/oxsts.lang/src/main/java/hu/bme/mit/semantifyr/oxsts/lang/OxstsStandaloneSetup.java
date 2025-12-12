/*
 * SPDX-FileCopyrightText: 2023-2024 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang;

import com.google.inject.Injector;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.OxstsPackage;
import org.eclipse.emf.ecore.EPackage;

/**
 * Initialization support for running Xtext languages without Equinox extension registry.
 */
public class OxstsStandaloneSetup extends OxstsStandaloneSetupGenerated {

    @Override
    public Injector createInjectorAndDoEMFRegistration() {
        if (!EPackage.Registry.INSTANCE.containsKey(OxstsPackage.eNS_URI)) {
            EPackage.Registry.INSTANCE.put(OxstsPackage.eNS_URI, OxstsPackage.eINSTANCE);
        }
        return super.createInjectorAndDoEMFRegistration();
    }

    public static void doSetup() {
        new OxstsStandaloneSetup().createInjectorAndDoEMFRegistration();
    }
}
