/*
 * generated by Xtext 2.32.0
 */
package hu.bme.mit.gamma.oxsts.lang;


/**
 * Initialization support for running Xtext languages without Equinox extension registry.
 */
public class OxstsStandaloneSetup extends OxstsStandaloneSetupGenerated {

	public static void doSetup() {
		new OxstsStandaloneSetup().createInjectorAndDoEMFRegistration();
	}
}
