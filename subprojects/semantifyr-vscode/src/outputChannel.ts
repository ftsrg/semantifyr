/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import vscode, {type ExtensionContext} from "vscode";

export let outputChannel: vscode.OutputChannel;

export function writeToOutputChannel(message: string, reveal = true) {
    outputChannel.append(message);
    if (reveal) {
        outputChannel.show(true);
    }
}

export function registerOutputChannel(context: ExtensionContext) {
    outputChannel = vscode.window.createOutputChannel("Semantifyr");
    context.subscriptions.push(outputChannel);
}

export async function writeSuccessMessage(message: string) {
    await vscode.window.showInformationMessage(message);
    writeToOutputChannel(message);
}

export async function writeErrorMessage(message: string) {
    await vscode.window.showErrorMessage(message);
    writeToOutputChannel(message);
}
