/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.gradle.conventions

import org.gradle.api.Project
import org.gradle.api.provider.Provider

fun Project.testBrowser(): Provider<String> {
    return providers.gradleProperty("testBrowser").orElse("chromium")
}
