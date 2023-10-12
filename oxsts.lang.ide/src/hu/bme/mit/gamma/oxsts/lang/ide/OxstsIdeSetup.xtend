/*
 * generated by Xtext 2.31.0
 */
package hu.bme.mit.gamma.oxsts.lang.ide

import com.google.inject.Guice
import hu.bme.mit.gamma.oxsts.lang.OxstsRuntimeModule
import hu.bme.mit.gamma.oxsts.lang.OxstsStandaloneSetup
import org.eclipse.xtext.util.Modules2

/**
 * Initialization support for running Xtext languages as language servers.
 */
class OxstsIdeSetup extends OxstsStandaloneSetup {

	override createInjector() {
		Guice.createInjector(Modules2.mixin(new OxstsRuntimeModule, new OxstsIdeModule))
	}
	
}