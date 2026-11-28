/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.gradle

import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.attributes.Attribute
import org.gradle.api.attributes.AttributeContainer
import org.gradle.api.attributes.Category
import org.gradle.api.attributes.LibraryElements
import org.gradle.api.attributes.Usage
import org.gradle.api.file.FileCollection
import org.gradle.kotlin.dsl.named

private val SEMANTIFYR_ARTIFACT_ATTRIBUTE = Attribute.of("hu.bme.mit.semantifyr.artifact", String::class.java)

fun Project.semantifyrArtifact(kind: String): Action<AttributeContainer> {
    dependencies.attributesSchema.attribute(SEMANTIFYR_ARTIFACT_ATTRIBUTE)
    return Action {
        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.LIBRARY))
        attribute(SEMANTIFYR_ARTIFACT_ATTRIBUTE, kind)
    }
}

fun Project.artifactsOf(kind: String, vararg projectPaths: String): FileCollection {
    val name = listOf(kind, *projectPaths).joinToString("")
    val filesName = "${name}Files"
    if (filesName !in configurations.names) {
        val dependencyScope = configurations.dependencyScope(name)
        projectPaths.forEach {
            dependencies.add(name, dependencies.project(it))
        }
        configurations.resolvable(filesName) {
            extendsFrom(dependencyScope.get())
            attributes(semantifyrArtifact(kind))
        }
    }
    return files(configurations.named(filesName))
}

fun Project.javaRuntimeLibrary(): Action<AttributeContainer> {
    return Action {
        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.LIBRARY))
        attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME))
        attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objects.named(LibraryElements.JAR))
    }
}
