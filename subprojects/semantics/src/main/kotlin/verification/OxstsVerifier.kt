/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.semantics.verification

import com.google.inject.Inject
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ClassDeclaration
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlinedOxsts
import hu.bme.mit.semantifyr.semantics.transformation.OxstsClassInliner
import hu.bme.mit.semantifyr.semantics.transformation.ProgressContext
import hu.bme.mit.semantifyr.semantics.transformation.backannotation.AssumptionWitnessBackAnnotator
import hu.bme.mit.semantifyr.semantics.transformation.backannotation.InlinedOxstsAssumptionWitness
import hu.bme.mit.semantifyr.semantics.transformation.backannotation.OxstsClassAssumptionWitnessTransformer
import hu.bme.mit.semantifyr.semantics.transformation.serializer.ArtifactManager
import hu.bme.mit.semantifyr.semantics.transformation.tracer.TraceSerializer
import kotlinx.serialization.Serializable
import java.util.concurrent.TimeUnit

@Serializable
data class VerificationCaseRunResult(
    val result: VerificationResult,
    val message: String? = null
)

enum class VerificationResult {
    Passed, Failed, Errored
}

interface OxstsVerifier {

    fun verify(
        progressContext: ProgressContext,
        classDeclaration: ClassDeclaration,
        timeout: Long = 10L,
        timeUnit: TimeUnit = TimeUnit.MINUTES
    ): VerificationCaseRunResult

}

abstract class AbstractOxstsVerifier : OxstsVerifier {

    @Inject
    private lateinit var oxstsClassInliner: OxstsClassInliner

    @Inject
    private lateinit var assumptionWitnessBackAnnotator: AssumptionWitnessBackAnnotator

    @Inject
    private lateinit var oxstsClassAssumptionWitnessTransformer: OxstsClassAssumptionWitnessTransformer

    @Inject
    private lateinit var traceSerializer: TraceSerializer

    @Inject
    private lateinit var artifactManager: ArtifactManager

    open fun inlineClass(progressContext: ProgressContext, classDeclaration: ClassDeclaration): InlinedOxsts {
        return oxstsClassInliner.inline(progressContext, classDeclaration)
    }

    open fun backAnnotateWitness(inlinedOxstsAssumptionWitness: InlinedOxstsAssumptionWitness) {
        val classWitness = oxstsClassAssumptionWitnessTransformer.transform(inlinedOxstsAssumptionWitness)
        val witness = assumptionWitnessBackAnnotator.createWitnessInlinedOxsts(classWitness)

        traceSerializer.serialize(classWitness)

        val resourceSet = inlinedOxstsAssumptionWitness.inlinedOxsts.eResource().resourceSet
        val uri = artifactManager.resolveUri("witness.oxsts")
        resourceSet.getResource(uri, false)?.delete(mutableMapOf<Any, Any>())
        val resource = resourceSet.createResource(uri)
        resource.contents += witness
        resource.save(emptyMap<Any, Any>())
    }

}
