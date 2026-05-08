/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.live.backend.testing

import hu.bme.mit.semantifyr.live.backend.server.Flavor
import hu.bme.mit.semantifyr.live.backend.server.WorkspaceLayout
import hu.bme.mit.semantifyr.live.backend.session.SessionContext
import io.ktor.websocket.WebSocketSession
import org.mockito.kotlin.mock
import java.nio.file.Path
import kotlin.io.path.Path

fun testFlavor(
    id: String = "oxsts",
    binaryRelativePath: Path = Path("does-not-exist"),
    fileName: String = "snippet.oxsts",
    languageId: String = "oxsts",
    workspaceLayout: WorkspaceLayout = WorkspaceLayout.SingleFile,
    verificationCommand: String = "oxsts.case.verify",
    discoveryCommand: String = "oxsts.case.discover",
): Flavor {
    return Flavor(
        id = id,
        displayName = "Semantifyr",
        binaryRelativePath = binaryRelativePath,
        fileName = fileName,
        languageId = languageId,
        workspaceLayout = workspaceLayout,
        verificationCommand = verificationCommand,
        discoveryCommand = discoveryCommand,
    )
}

fun testSessionContext(
    workingDirectoryPath: Path,
    flavor: Flavor = testFlavor(),
    sessionId: String = "test-session",
    remoteIp: String = "127.0.0.1",
): SessionContext {
    return SessionContext(
        sessionId = sessionId,
        remoteIp = remoteIp,
        flavor = flavor,
        webSocketSession = mock<WebSocketSession>(),
        workingDirectoryPath = workingDirectoryPath,
    )
}
