/*
 * generated by Xtext 2.31.0
 */
package hu.bme.mit.gamma.oxsts.lang


/**
 * Initialization support for running Xtext languages without Equinox extension registry.
 */
class OxstsStandaloneSetup extends OxstsStandaloneSetupGenerated {

	def static void doSetup() {
		new OxstsStandaloneSetup().createInjectorAndDoEMFRegistration()
	}
}
