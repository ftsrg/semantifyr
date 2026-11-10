/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.verifier

class BackAnnotationException(
    message: String?,
    cause: Throwable,
) : RuntimeException(message, cause)
