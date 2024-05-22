/*
 * SPDX-FileCopyrightText: 2024 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang;

/**
 * Initialization support for running Xtext languages without Equinox extension registry.
 */
public class OxstsStandaloneSetup extends OxstsStandaloneSetupGenerated {

    public static void doSetup() {
        new OxstsStandaloneSetup().createInjectorAndDoEMFRegistration();
    }
}
