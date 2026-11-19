/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.frontends.gamma

import com.google.inject.Singleton
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.GammaModelPackage
import hu.bme.mit.semantifyr.frontends.gamma.serialization.GammaNameMap
import hu.bme.mit.semantifyr.frontends.gamma.serialization.GammaTime
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
    val timeUnitMilliseconds: Int,
)

@Singleton
class GammaCompiler {

    private val logger by loggerFactory()

    fun compile(gammaModel: GammaModelPackage, outputPath: Path): GammaNameMap {
        val nameMap = GammaNameMap()
        write(gammaModel, outputPath, nameMap, timeUnitMilliseconds(gammaModel))
        return nameMap
    }

    @OptIn(ExperimentalPathApi::class)
    fun compileWithImports(
        gammaModel: GammaModelPackage,
        outputDirectory: Path,
        fileExtension: String,
    ): CompiledGammaModel {
        val nameMap = GammaNameMap()
        val timeUnitMilliseconds = timeUnitMilliseconds(gammaModel)
        val modelPath = modelPath(outputDirectory, fileExtension)
        write(gammaModel, modelPath, nameMap, timeUnitMilliseconds)

        val importsDirectory = importsDirectory(outputDirectory)
        importsDirectory.deleteRecursively()
        val fileNames = mutableSetOf<String>()
        val importedPaths = reachablePackages(gammaModel).map {
            val baseName = it.eResource().uri.trimFileExtension().lastSegment()
            var fileName = "$baseName.$fileExtension"
            var number = 2
            while (!fileNames.add(fileName)) {
                fileName = "${baseName}_$number.$fileExtension"
                number++
            }
            val path = importsDirectory.resolve(fileName)
            write(it, path, nameMap, timeUnitMilliseconds)
            path
        }
        return CompiledGammaModel(modelPath, importedPaths, nameMap, timeUnitMilliseconds)
    }

    private fun write(
        gammaModel: GammaModelPackage,
        outputPath: Path,
        nameMap: GammaNameMap,
        timeUnitMilliseconds: Int,
    ) {
        logger.info { "Compiling Gamma package '${gammaModel.name}' to '$outputPath'" }
        val output = GammaToOxstsSerializer(nameMap, timeUnitMilliseconds).transformToOxsts(gammaModel)
        outputPath.parent?.createDirectories()
        outputPath.writeText(output)
    }

    companion object {
        fun modelPath(outputDirectory: Path, fileExtension: String): Path {
            return outputDirectory.resolve("model.$fileExtension")
        }

        fun importsDirectory(outputDirectory: Path): Path {
            return outputDirectory.resolve("imports")
        }

        @JvmStatic
        fun timeUnitMilliseconds(gammaModel: GammaModelPackage): Int {
            return GammaTime.unitMilliseconds(listOf(gammaModel) + reachablePackages(gammaModel))
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
    }

}
