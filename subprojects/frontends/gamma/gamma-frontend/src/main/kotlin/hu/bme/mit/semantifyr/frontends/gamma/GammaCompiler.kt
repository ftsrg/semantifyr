/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.frontends.gamma

import com.google.inject.Inject
import com.google.inject.Provider
import com.google.inject.Singleton
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.GammaModelPackage
import hu.bme.mit.semantifyr.frontends.gamma.serialization.GammaToOxstsSerializer
import hu.bme.mit.semantifyr.logging.info
import hu.bme.mit.semantifyr.logging.loggerFactory
import java.nio.file.Path

@Singleton
class GammaCompiler @Inject constructor(
    private val gammaToOxstsSerializerProvider: Provider<GammaToOxstsSerializer>,
) {

    private val logger by loggerFactory()

    fun compile(gammaModel: GammaModelPackage, outputPath: Path) {
        logger.info { "Compiling Gamma package '${gammaModel.name}'" }
        val output = serialize(gammaModel)
        val outputFile = outputPath.toFile()
        outputFile.parentFile?.mkdirs()
        outputFile.writeText(output)
    }

    private fun serialize(gammaModel: GammaModelPackage): String {
        logger.info { "Serializing Gamma package '${gammaModel.name}'" }
        return gammaToOxstsSerializerProvider.get().transformToOxsts(gammaModel)
    }

}
