/*
 * SPDX-FileCopyrightText: 2023-2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import {ExtensionContext} from 'vscode';
import {registerOutputChannel} from "./outputChannel.js";
import {startClients, stopClients} from "./clients/clients.js";
import { registerCommands } from './commands.js';

export async function activate(context: ExtensionContext) {
    registerOutputChannel(context);
    registerCommands(context);

    await startClients(context);
}

export async function deactivate() {
    await stopClients();
}
