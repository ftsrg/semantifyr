/*
 * SPDX-FileCopyrightText: 2023-2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import {ExtensionContext} from 'vscode';
import {registerOutputChannel} from "./outputChannel.js";
import {startClients, stopClients} from "./clients.js";

export async function activate(context: ExtensionContext) {
    registerOutputChannel(context);

    await startClients(context);
}

export async function deactivate() {
    await stopClients();
}
