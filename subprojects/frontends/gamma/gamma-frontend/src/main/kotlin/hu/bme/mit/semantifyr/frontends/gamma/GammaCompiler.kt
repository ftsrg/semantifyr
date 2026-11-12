/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.frontends.gamma

import com.google.inject.Singleton
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.GammaModelPackage
import hu.bme.mit.semantifyr.frontends.gamma.serialization.GammaNameMap
import hu.bme.mit.semantifyr.frontends.gamma.serialization.GammaToOxstsSerializer
import hu.bme.mit.semantifyr.logging.info
import hu.bme.mit.semantifyr.logging.loggerFactory
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteRecursively
import kotlin.io.path.writeText

class CompiledGammaModel(
    val modelPath: Path,
    val importedPaths: List<Path>,
    val nameMap: GammaNameMap,
)

@Singleton
class GammaCompiler {

    private val logger by loggerFactory()

    fun compile(gammaModel: GammaModelPackage, outputPath: Path): GammaNameMap {
        val nameMap = GammaNameMap()
        write(gammaModel, outputPath, nameMap)
        return nameMap
    }

    // every package the model reaches through its imports or its own name is emitted as an OXSTS package of its own
    @OptIn(ExperimentalPathApi::class)
    fun compileWithImports(gammaModel: GammaModelPackage, outputDirectory: Path): CompiledGammaModel {
        val nameMap = GammaNameMap()
        val modelPath = outputDirectory.resolve(MODEL_FILE_NAME)
        write(gammaModel, modelPath, nameMap)

        val importsDirectory = importsDirectory(outputDirectory)
        importsDirectory.deleteRecursively()
        val fileNames = mutableSetOf<String>()
        val importedPaths = reachablePackages(gammaModel).map {
            val baseName = it.eResource().uri.trimFileExtension().lastSegment()
            var fileName = "$baseName.oxsts"
            var number = 2
            while (!fileNames.add(fileName)) {
                fileName = "${baseName}_$number.oxsts"
                number++
            }
            val path = importsDirectory.resolve(fileName)
            write(it, path, nameMap)
            path
        }
        return CompiledGammaModel(modelPath, importedPaths, nameMap)
    }

    private fun reachablePackages(gammaModel: GammaModelPackage): List<GammaModelPackage> {
        val reached = linkedSetOf(gammaModel)
        val pending = ArrayDeque(listOf(gammaModel))
        while (pending.isNotEmpty()) {
            val current = pending.removeFirst()
            val importedPackages = current.imports.map {
                it.importedPackage
            }
            val packageNames = importedPackages.map {
                it.name
            }.toSet() + current.name
            val loadedPackages = current.eResource().resourceSet.resources.flatMap {
                it.contents
            }.filterIsInstance<GammaModelPackage>().filter {
                it.name in packageNames
            }
            for (candidate in importedPackages + loadedPackages) {
                if (reached.add(candidate)) {
                    pending += candidate
                }
            }
        }
        return reached.drop(1)
    }

    private fun write(gammaModel: GammaModelPackage, outputPath: Path, nameMap: GammaNameMap) {
        logger.info { "Compiling Gamma package '${gammaModel.name}' to '$outputPath'" }
        val output = GammaToOxstsSerializer(nameMap).transformToOxsts(gammaModel)
        outputPath.parent?.createDirectories()
        outputPath.writeText(output)
    }

    companion object {
        const val MODEL_FILE_NAME = "model.oxsts"

        fun importsDirectory(outputDirectory: Path): Path {
            return outputDirectory.resolve("imports")
        }
    }

}
