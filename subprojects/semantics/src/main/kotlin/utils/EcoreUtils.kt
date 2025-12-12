/*
 * SPDX-FileCopyrightText: 2023-2024 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.semantics.utils

import org.eclipse.emf.ecore.EObject
import org.eclipse.xtext.EcoreUtil2

fun <T : EObject> T.copy(): T = EcoreUtil2.copy(this)

inline fun <reified T : EObject> EObject.eAllOfType() = EcoreUtil2.eAll(this).asSequence().filterIsInstance<T>()
