/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.gradle.conventions

import org.gradle.api.Project

// the grammar workflow lists the language's file extensions, the one Semantifyr writes first
val Project.oxstsFileExtension: String
    get() {
        val workflow = providers.fileContents(rootProject.layout.projectDirectory.file("subprojects/oxsts.lang/src/main/java/hu/bme/mit/semantifyr/oxsts/lang/GenerateOxsts.mwe2")).asText.get()
        val extensions = Regex("""fileExtensions = "([^"]+)"""").find(workflow) ?: error("The grammar workflow lists no file extension")
        return extensions.groupValues[1].substringBefore(',')
    }
