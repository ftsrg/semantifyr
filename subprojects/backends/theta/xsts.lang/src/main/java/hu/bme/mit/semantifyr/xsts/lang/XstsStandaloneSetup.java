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

    public static void doSetup() {
        new XstsStandaloneSetup().createInjectorAndDoEMFRegistration();
    }

}
