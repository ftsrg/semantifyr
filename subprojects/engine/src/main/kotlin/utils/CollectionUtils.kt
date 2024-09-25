/*
 * SPDX-FileCopyrightText: 2023-2024 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.engine.utils

inline fun <reified T, reified C : Set<T>> C.except(other: T) = filter { it != other }.toSet()
