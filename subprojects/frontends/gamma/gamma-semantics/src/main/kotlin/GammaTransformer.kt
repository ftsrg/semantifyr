/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.frontends.gamma.semantics

import hu.bme.mit.semantifyr.frontends.gamma.lang.GammaStandaloneSetup
import hu.bme.mit.semantifyr.frontends.gamma.semantics.reader.GammaReader
import hu.bme.mit.semantifyr.frontends.gamma.semantics.serialization.GammaToOxstsSerializer
import java.io.File

class StandaloneGammaTransformer {

    fun transformModel(modelPath: File, outputPath: File? = null) {
        val injector = GammaStandaloneSetup().createInjectorAndDoEMFRegistration()
        val reader = injector.getInstance(GammaReader::class.java)

        val gammaModel = reader.readGammaFile(modelPath)

        val serializer = injector.getInstance(GammaToOxstsSerializer::class.java)
        val oxstsModel = serializer.transformToOxsts(gammaModel)
        val outputFile = outputPath ?: File(modelPath.absolutePath.replace(".gamma", ".oxsts"))

        outputFile.writeText(oxstsModel)
    }

}
