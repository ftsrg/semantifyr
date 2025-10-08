/*
 * SPDX-FileCopyrightText: 2023-2024 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.xsts.lang;


import com.google.inject.Injector;
import hu.bme.mit.semantifyr.xsts.lang.xsts.XstsPackage;
import org.eclipse.emf.ecore.EPackage;

/**
 * Initialization support for running Xtext languages without Equinox extension registry.
 */
public class XstsStandaloneSetup extends XstsStandaloneSetupGenerated {

    @Override
    public Injector createInjectorAndDoEMFRegistration() {
        if (!EPackage.Registry.INSTANCE.containsKey(XstsPackage.eNS_URI)) {
            EPackage.Registry.INSTANCE.put(XstsPackage.eNS_URI, XstsPackage.eINSTANCE);
        }
        return super.createInjectorAndDoEMFRegistration();
    }

    public static void doSetup() {
        new XstsStandaloneSetup().createInjectorAndDoEMFRegistration();
    }

}
