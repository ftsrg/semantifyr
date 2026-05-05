/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.frontends.gamma

import com.google.inject.Inject
import com.google.inject.Provider
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.GammaModelPackage
import hu.bme.mit.semantifyr.frontends.gamma.serialization.GammaToOxstsSerializer
import hu.bme.mit.semantifyr.logging.debug
import hu.bme.mit.semantifyr.logging.info
import hu.bme.mit.semantifyr.logging.loggerFactory
import java.nio.file.Files
import java.nio.file.Path

class GammaCompiler @Inject constructor(
    private val gammaToOxstsSerializerProvider: Provider<GammaToOxstsSerializer>,
) {

    private val logger by loggerFactory()

    fun compile(gammaModel: GammaModelPackage, outputPath: Path) {
        logger.info { "Compiling Gamma package '${gammaModel.name}' to '$outputPath'" }
        val serializer = gammaToOxstsSerializerProvider.get()
        val serialized = serializer.transformToOxsts(gammaModel)

        val parent = outputPath.parent
        if (parent != null) {
            Files.createDirectories(parent)
        }

        Files.writeString(outputPath, serialized)
        logger.debug { "Wrote ${serialized.length} bytes of OXSTS to '$outputPath'" }
    }

}
