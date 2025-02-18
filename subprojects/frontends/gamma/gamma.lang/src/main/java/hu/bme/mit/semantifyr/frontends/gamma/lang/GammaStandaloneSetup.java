/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.frontends.gamma.lang;


/**
 * Initialization support for running Xtext languages without Equinox extension registry.
 */
public class GammaStandaloneSetup extends GammaStandaloneSetupGenerated {

	public static void doSetup() {
		new GammaStandaloneSetup().createInjectorAndDoEMFRegistration();
	}
}
