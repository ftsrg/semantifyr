/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import { ExtensionContext } from "vscode";
import { startGammaClient as startGammaClient, stopGammaClient as stopGammaClient } from "./gamma-client.js";
import { startOxstsClient, stopOxstsClient } from "./oxsts-client.js";
import { startXstsClient, stopXstsClient } from "./xsts-client.js";

export async function startClients(context: ExtensionContext) {
    await startOxstsClient(context);
    await startXstsClient(context);
    await startGammaClient(context);
}

export async function stopClients() {
    await stopOxstsClient();
    await stopXstsClient();
    await stopGammaClient();
}
