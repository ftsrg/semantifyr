/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.frontends.gamma.lang.metamodel

import hu.bme.mit.semantifyr.oxsts.model.metamodel.generateModel
import java.io.File

fun main(args: Array<String>) {
    generateModel(Gamma, File(args[0]).absoluteFile, File(args[1]).absoluteFile)
}
