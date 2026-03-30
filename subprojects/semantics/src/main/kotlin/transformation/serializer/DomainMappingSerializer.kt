/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.semantics.transformation.serializer

import com.google.inject.Inject
import hu.bme.mit.semantifyr.oxsts.lang.serializer.ExpressionSerializer
import hu.bme.mit.semantifyr.semantics.expression.DeflatedExpressionEvaluationTransformer
import hu.bme.mit.semantifyr.semantics.expression.InstanceReferenceProvider
import hu.bme.mit.semantifyr.semantics.transformation.injection.scope.CompilationScoped
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToStream

@Serializable
data class DomainIdMapping(
    val name: String,
    val ids: List<Int>
)

@Serializable
data class InstanceIdMapping(
    val name: String,
    val id: Int
)

@Serializable
data class SerializableDomainMapping(
    val instances: List<InstanceIdMapping>
//    val domains: List<DomainIdMapping>,
)

@CompilationScoped
class DomainMappingSerializer {

    @Inject
    private lateinit var deflatedEvaluationTransformer: DeflatedExpressionEvaluationTransformer

    @Inject
    private lateinit var instanceReferenceProvider: InstanceReferenceProvider

    @Inject
    private lateinit var expressionSerializer: ExpressionSerializer

    @Inject
    private lateinit var artifactManager: ArtifactManager

    fun serializeMapping() {
        val instanceIds = deflatedEvaluationTransformer.evaluateMapping()

        val data = SerializableDomainMapping(
            instanceIds.map { (instance, id) ->
                val reference = instanceReferenceProvider.getReference(instance)
                InstanceIdMapping(
                    expressionSerializer.serialize(reference), id,
                )
            },
        )

        val file = artifactManager.resolve("mapping.json")

        val json = Json {
            prettyPrint = true
            prettyPrintIndent = "  "
            explicitNulls = false
        }

        file.outputStream().buffered().use {
            json.encodeToStream(data, it)
        }

    }

}
