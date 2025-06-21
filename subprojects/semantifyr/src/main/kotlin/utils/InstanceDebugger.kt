/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.semantifyr.utils

import hu.bme.mit.semantifyr.oxsts.model.oxsts.Containment
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Derived
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Feature
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Instance
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Reference

fun serializeInstance(instance: Instance): String {
    return indent {
        appendInstance(instance)
    }
}

private fun IndentationAwareStringWriter.appendInstance(instance: Instance) {
    appendIndent("instance ${instance.name} : ${instance.type.name} :> ${instance.containment.realName}") {
        for (feature in instance.containment.allFeatures.distinctBy { it.realName }) {
            appendAssociation(instance, feature)
        }

        if (instance.children.isNotEmpty()) {
            appendLine()
        }

        for (child in instance.children) {
            appendInstance(child)
        }
    }
}

private fun IndentationAwareStringWriter.appendAssociation(container: Instance, feature: Feature) {
    if (feature is Containment) return

    val value = container.contextualEvaluator.evaluate(OxstsFactory.createChainReferenceExpression(feature))

    when (feature) {
        is Reference -> appendLine("${feature.realName} -> $value")
        is Derived -> appendLine("derived ${feature.realName} -> $value")
    }
}
