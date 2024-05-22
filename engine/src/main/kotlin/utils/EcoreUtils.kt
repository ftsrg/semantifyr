/*
 * SPDX-FileCopyrightText: 2024 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.engine.utils

import org.eclipse.emf.ecore.EObject
import org.eclipse.xtext.EcoreUtil2

fun <T : EObject> T.copy() = EcoreUtil2.copy(this)
fun <T : EObject> Collection<T>.copy() = map { it.copy() }
