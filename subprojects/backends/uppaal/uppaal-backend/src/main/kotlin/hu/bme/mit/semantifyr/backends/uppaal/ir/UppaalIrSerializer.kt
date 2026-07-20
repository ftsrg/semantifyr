/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.uppaal.ir

import hu.bme.mit.semantifyr.utils.text.IndentingStringBuilder
import hu.bme.mit.semantifyr.utils.text.escapeXml

class UppaalIrSerializer {
    fun serialize(nta: UppaalNta): String {
        val builder = IndentingStringBuilder()
        builder.appendLine("<?xml version=\"1.0\" encoding=\"utf-8\"?>")
        builder.appendLine("<!DOCTYPE nta PUBLIC \"-//Uppaal Team//DTD Flat System 1.6//EN\" \"http://www.it.uu.se/research/group/darts/uppaal/flat-1_6.dtd\">")
        builder.appendLine("<nta>")
        builder.indented {
            serializeDeclarations(this, nta.globalDeclarations)
            for (template in nta.templates) {
                serializeTemplate(this, template)
            }
            serializeSystem(this, nta.systemDeclaration)
        }
        builder.appendLine("</nta>")
        return builder.toString()
    }

    private fun serializeDeclarations(
        builder: IndentingStringBuilder,
        declarations: UppaalDeclarations,
    ) {
        val text = renderDeclarationsText(declarations)
        if (text.isEmpty()) {
            builder.appendLine("<declaration></declaration>")
            return
        }
        builder.appendLine("<declaration>${escapeXml(text)}</declaration>")
    }

    private fun renderDeclarationsText(declarations: UppaalDeclarations): String {
        val lines = mutableListOf<String>()
        for (typedef in declarations.typedefs) {
            lines += typedef
        }
        for (variable in declarations.variables) {
            lines += renderVariable(variable)
        }
        return lines.joinToString("\n")
    }

    private fun renderVariable(variable: UppaalVariableDecl): String {
        val init = variable.initialValue?.let { " = $it" } ?: ""
        return "${variable.typeName} ${variable.name}$init;"
    }

    private fun serializeTemplate(
        builder: IndentingStringBuilder,
        template: UppaalTemplate,
    ) {
        builder.appendLine("<template>")
        builder.indented {
            appendLine("<name>${escapeXml(template.name)}</name>")
            if (template.parameters.isNotEmpty()) {
                appendLine("<parameter>${escapeXml(template.parameters)}</parameter>")
            }
            val localText = renderDeclarationsText(template.localDeclarations)
            if (localText.isNotEmpty()) {
                appendLine("<declaration>${escapeXml(localText)}</declaration>")
            }
            for (location in template.locations) {
                serializeLocation(this, location)
            }
            appendLine("<init ref=\"${escapeXml(template.initialLocationId)}\"/>")
            for (edge in template.edges) {
                serializeEdge(this, edge)
            }
        }
        builder.appendLine("</template>")
    }

    private fun serializeLocation(
        builder: IndentingStringBuilder,
        location: UppaalLocation,
    ) {
        builder.appendLine("<location id=\"${escapeXml(location.id)}\">")
        builder.indented {
            appendLine("<name>${escapeXml(location.name)}</name>")
            location.invariant?.let {
                appendLine("<label kind=\"invariant\">${escapeXml(it)}</label>")
            }
            when (location.kind) {
                UppaalLocationKind.Committed -> {
                    appendLine("<committed/>")
                }
                UppaalLocationKind.Urgent -> {
                    appendLine("<urgent/>")
                }
                UppaalLocationKind.Normal -> { /* no marker */ }
            }
        }
        builder.appendLine("</location>")
    }

    private fun serializeEdge(
        builder: IndentingStringBuilder,
        edge: UppaalEdge,
    ) {
        builder.appendLine("<transition>")
        builder.indented {
            appendLine("<source ref=\"${escapeXml(edge.sourceId)}\"/>")
            appendLine("<target ref=\"${escapeXml(edge.targetId)}\"/>")
            edge.select?.let {
                appendLine("<label kind=\"select\">${escapeXml(it)}</label>")
            }
            edge.guard?.let {
                appendLine("<label kind=\"guard\">${escapeXml(it)}</label>")
            }
            edge.sync?.let {
                appendLine("<label kind=\"synchronisation\">${escapeXml(it)}</label>")
            }
            edge.assignment?.let {
                appendLine("<label kind=\"assignment\">${escapeXml(it)}</label>")
            }
        }
        builder.appendLine("</transition>")
    }

    private fun serializeSystem(
        builder: IndentingStringBuilder,
        systemDeclaration: String,
    ) {
        builder.appendLine("<system>${escapeXml(systemDeclaration)}</system>")
    }
}
