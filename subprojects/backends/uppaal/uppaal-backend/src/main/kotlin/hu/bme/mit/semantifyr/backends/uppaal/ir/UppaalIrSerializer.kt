/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.uppaal.ir

import hu.bme.mit.semantifyr.backend.text.IndentingBuilder
import hu.bme.mit.semantifyr.backend.text.escapeXml

class UppaalIrSerializer {
    fun serialize(nta: UppaalNta): String {
        val builder = IndentingBuilder()
        builder.line("<?xml version=\"1.0\" encoding=\"utf-8\"?>")
        builder.line("<!DOCTYPE nta PUBLIC \"-//Uppaal Team//DTD Flat System 1.6//EN\" \"http://www.it.uu.se/research/group/darts/uppaal/flat-1_6.dtd\">")
        builder.line("<nta>")
        builder.indented {
            serializeDeclarations(this, nta.globalDeclarations)
            for (template in nta.templates) {
                serializeTemplate(this, template)
            }
            serializeSystem(this, nta.systemDeclaration)
        }
        builder.line("</nta>")
        return builder.toString()
    }

    private fun serializeDeclarations(
        builder: IndentingBuilder,
        declarations: UppaalDeclarations,
    ) {
        val text = renderDeclarationsText(declarations)
        if (text.isEmpty()) {
            builder.line("<declaration></declaration>")
            return
        }
        builder.line("<declaration>${escapeXml(text)}</declaration>")
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
        builder: IndentingBuilder,
        template: UppaalTemplate,
    ) {
        builder.line("<template>")
        builder.indented {
            line("<name>${escapeXml(template.name)}</name>")
            if (template.parameters.isNotEmpty()) {
                line("<parameter>${escapeXml(template.parameters)}</parameter>")
            }
            val localText = renderDeclarationsText(template.localDeclarations)
            if (localText.isNotEmpty()) {
                line("<declaration>${escapeXml(localText)}</declaration>")
            }
            for (location in template.locations) {
                serializeLocation(this, location)
            }
            line("<init ref=\"${escapeXml(template.initialLocationId)}\"/>")
            for (edge in template.edges) {
                serializeEdge(this, edge)
            }
        }
        builder.line("</template>")
    }

    private fun serializeLocation(
        builder: IndentingBuilder,
        location: UppaalLocation,
    ) {
        builder.line("<location id=\"${escapeXml(location.id)}\">")
        builder.indented {
            line("<name>${escapeXml(location.name)}</name>")
            location.invariant?.let {
                line("<label kind=\"invariant\">${escapeXml(it)}</label>")
            }
            when (location.kind) {
                UppaalLocationKind.Committed -> {
                    line("<committed/>")
                }
                UppaalLocationKind.Urgent -> {
                    line("<urgent/>")
                }
                UppaalLocationKind.Normal -> { /* no marker */ }
            }
        }
        builder.line("</location>")
    }

    private fun serializeEdge(
        builder: IndentingBuilder,
        edge: UppaalEdge,
    ) {
        builder.line("<transition>")
        builder.indented {
            line("<source ref=\"${escapeXml(edge.sourceId)}\"/>")
            line("<target ref=\"${escapeXml(edge.targetId)}\"/>")
            edge.select?.let {
                line("<label kind=\"select\">${escapeXml(it)}</label>")
            }
            edge.guard?.let {
                line("<label kind=\"guard\">${escapeXml(it)}</label>")
            }
            edge.sync?.let {
                line("<label kind=\"synchronisation\">${escapeXml(it)}</label>")
            }
            edge.assignment?.let {
                line("<label kind=\"assignment\">${escapeXml(it)}</label>")
            }
        }
        builder.line("</transition>")
    }

    private fun serializeSystem(
        builder: IndentingBuilder,
        systemDeclaration: String,
    ) {
        builder.line("<system>${escapeXml(systemDeclaration)}</system>")
    }
}
