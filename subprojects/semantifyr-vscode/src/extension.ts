/*
 * SPDX-FileCopyrightText: 2023-2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import {ExtensionContext} from 'vscode';
import {registerCodeLensProvider} from "./codeLensProvider";
import {registerOutputChannel} from "./outputChannel";
import {registerCommands} from "./commands";
import {startClients, stopClients} from "./clients";

export async function activate(context: ExtensionContext) {
    registerOutputChannel(context);
    registerCodeLensProvider(context);
    registerCommands(context);

    await startClients(context);
}

export async function deactivate() {
    await stopClients();
}
