/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.gradle.conventions

import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters

abstract class VerificationTestService : BuildService<BuildServiceParameters.None>
