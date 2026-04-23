/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler.pipeline.artifact

import com.google.inject.Inject
import hu.bme.mit.semantifyr.compiler.pipeline.context.InstanceIdMapping
import hu.bme.mit.semantifyr.compiler.pipeline.expression.InstanceReferenceProvider
import hu.bme.mit.semantifyr.oxsts.lang.serializer.ExpressionSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToStream

@Serializable
data class DomainIdMapping(
    val name: String,
    val ids: List<Int>
)

@Serializable
data class SerializableInstanceIdMapping(
    val name: String,
    val id: Int
)

@Serializable
data class SerializableDomainMapping(
    val instances: List<SerializableInstanceIdMapping>
//    val domains: List<DomainIdMapping>,
)

class DomainMappingSerializer @Inject constructor(
    private val instanceReferenceProvider: InstanceReferenceProvider,
    private val expressionSerializer: ExpressionSerializer,
    private val artifactManager: ArtifactManager,
) {

    fun serializeMapping(instanceIdMapping: InstanceIdMapping) {
        val data = SerializableDomainMapping(
            instanceIdMapping.entries.map { (instance, id) ->
                val reference = instanceReferenceProvider.getReference(instance)
                SerializableInstanceIdMapping(
                    expressionSerializer.serialize(reference), id,
                )
            },
        )

        artifactManager.withFile(ArtifactKind.Mapping) {
            val json = Json {
                prettyPrint = true
                prettyPrintIndent = "  "
                explicitNulls = false
            }

            it.outputStream().buffered().use {
                json.encodeToStream(data, it)
            }
        }

    }

}
