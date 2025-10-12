/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.frontends.gamma.lang;


import com.google.inject.Injector;
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.GammaPackage;
import org.eclipse.emf.ecore.EPackage;

/**
 * Initialization support for running Xtext languages without Equinox extension registry.
 */
public class GammaStandaloneSetup extends GammaStandaloneSetupGenerated {

    @Override
    public Injector createInjectorAndDoEMFRegistration() {
        if (!EPackage.Registry.INSTANCE.containsKey(GammaPackage.eNS_URI)) {
            EPackage.Registry.INSTANCE.put(GammaPackage.eNS_URI, GammaPackage.eINSTANCE);
        }
        return super.createInjectorAndDoEMFRegistration();
    }

	public static void doSetup() {
		new GammaStandaloneSetup().createInjectorAndDoEMFRegistration();
	}
}
