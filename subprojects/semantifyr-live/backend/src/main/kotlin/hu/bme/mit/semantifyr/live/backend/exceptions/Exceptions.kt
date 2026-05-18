/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.live.backend.exceptions

class InvalidConfigurationException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

class WorkspaceUriException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

class SessionLimitReachedException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
