/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.semantifyr.transformation

import hu.bme.mit.semantifyr.oxsts.model.oxsts.Instance
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Pattern
import hu.bme.mit.semantifyr.oxsts.model.oxsts.XSTS
import hu.bme.mit.semantifyr.oxsts.semantifyr.serialization.XstsSerializer
import hu.bme.mit.semantifyr.oxsts.semantifyr.utils.serializeInstance
import java.io.File
import java.nio.file.Files

open class OptionalArtifactPersistor(
    val file: File
) {
    var isEnabled = false

    inline fun write(writer: (File) -> Unit) {
        if (isEnabled) {
            if (file.isDirectory) {
                file.mkdirs()
            } else {
                file.parentFile.mkdirs()
            }

            writer(file)
        }
    }
}

class PatternArtifactPersistor(
    file: File
) : OptionalArtifactPersistor(file) {

    fun persist(pattern: Pattern, vql: String) {
        write { parent ->
            parent.mkdirs()
            val intermediate = parent.resolve("${pattern.name}.vql")
            intermediate.writeText(vql)
        }
    }

}

class InstanceArtifactPersistor(
    file: File
) : OptionalArtifactPersistor(file) {

    fun persist(instance: Instance) {
        write { file ->
            file.parentFile.mkdirs()
            file.writeText(serializeInstance(instance))
        }
    }

}

class IntermediateXstsArtifactPersistor (
    file: File
) : OptionalArtifactPersistor(file) {

    private var id = 0

    fun persist(xsts: XSTS) {
        write { parent ->
            parent.mkdirs()
            val intermediate = parent.resolve("${id++}.xsts")
            val content = try {
                XstsSerializer.serialize(xsts)
            } catch (throwable: Throwable) {
                throwable.stackTraceToString()
            }

            intermediate.writeText(content)
        }
    }

}

open class ArtifactManager(
    val baseDirectory: File
) {

    val patternPersistor = PatternArtifactPersistor(baseDirectory.resolve("patterns"))
    val instancePersistor = InstanceArtifactPersistor(baseDirectory.resolve("instance"))
    val intermediateXstsPersistor = IntermediateXstsArtifactPersistor(baseDirectory.resolve("intermediate_xsts"))

}

object DefaultArtifactManager : ArtifactManager(Files.createTempDirectory("semantifyr").toFile())
