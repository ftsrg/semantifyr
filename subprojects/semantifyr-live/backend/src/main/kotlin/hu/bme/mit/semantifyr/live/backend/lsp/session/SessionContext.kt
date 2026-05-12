/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.live.backend.lsp.session

import hu.bme.mit.semantifyr.live.backend.Flavor
import java.nio.file.Path

class SessionContext(
    val sessionId: String,
    val remoteIp: String,
    val flavor: Flavor,
    val workingDirectoryPath: Path,
)
