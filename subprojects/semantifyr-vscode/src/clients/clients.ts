/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import type { ExtensionContext } from "vscode";
import { startGammaClient, stopGammaClient } from "./gamma-client.js";
import { startOxstsClient, stopOxstsClient } from "./oxsts-client.js";
import { WitnessView } from "../witness.js";

export async function startClients(context: ExtensionContext) {
    const witnesses = new WitnessView();
    witnesses.register(context);
    await startOxstsClient(context, witnesses);
    await startGammaClient(context, witnesses);
}

export async function stopClients() {
    await stopOxstsClient();
    await stopGammaClient();
}
