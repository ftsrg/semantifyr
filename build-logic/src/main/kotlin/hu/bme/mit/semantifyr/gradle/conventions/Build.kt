/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.gradle.conventions

import org.gradle.api.Project
import org.gradle.language.base.plugins.LifecycleBasePlugin

fun Project.buildWithoutPackaging() {
    tasks.named(LifecycleBasePlugin.BUILD_TASK_NAME) {
        setDependsOn(dependsOn - LifecycleBasePlugin.ASSEMBLE_TASK_NAME)
    }
}
