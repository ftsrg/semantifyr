/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import vscode, {ExtensionContext} from "vscode";
import path from "path";
import childProcess from "child_process";
import {outputChannel, writeErrorMessage, writeSuccessMessage, writeToOutputChannel} from "./outputChannel.js";
import {executablePostfix} from "./runner-utils.js";

export async function executeProcess<R>(name: string, process: () => Thenable<R>) {
    vscode.window.showInformationMessage(name);
    await vscode.window.withProgress({
        location: vscode.ProgressLocation.Window,
        title: name,
        cancellable: false
    }, process);
}

async function waitForProcess(
    process: childProcess.ChildProcess, 
    name: string, 
    successMessage: string, 
    errorMessage: (error: Error) => string
) {
    await executeProcess(name, () => {
        return new Promise<void>((resolve, reject) => {
            outputChannel.clear();

            process.stdout?.on('data', (data: string) => {
                writeToOutputChannel(data);
            });
            process.stderr?.on('data', (data: string) => {
                writeToOutputChannel(data);
            });

            process.on('close', (code) => {
                if (code === 0) {
                    // We don't need to wait for the UI to complete.
                    // eslint-disable-next-line @typescript-eslint/no-floating-promises
                    writeSuccessMessage(successMessage);
                    resolve();
                } else {
                    const error = new Error(`Process exited with code ${code}`);
                    // We don't need to wait for the UI to complete.
                    // eslint-disable-next-line @typescript-eslint/no-floating-promises
                    writeErrorMessage(errorMessage(error));
                    reject(error);
                }
            });

            process.on('error', (error) => {
                // We don't need to wait for the UI to complete.
                // eslint-disable-next-line @typescript-eslint/no-floating-promises
                writeErrorMessage(errorMessage(error));
                reject(error);
            });
        });
    });
}




function registerCompileGammaCommand(context: ExtensionContext, gammaExecutable: string) {
    context.subscriptions.push(
        vscode.commands.registerCommand('gamma.compile', async (uri: vscode.Uri) => {
            const documentPath = uri.fsPath;
            const args = `${gammaExecutable} compile ${documentPath}`;

            await waitForProcess(
                childProcess.exec(args), 
                `Compiling Gamma model: ${documentPath}`, 
                `Success! Gamma model: ${documentPath}.`,
                (error) => `Error compiling target: ${error.message}.`
            );
        })
    );
}


export function registerCommands(context: ExtensionContext) {
    const gammaExecutable = path.join(context.extensionPath, 'bin', 'gamma-cli', 'bin', `gamma-cli${executablePostfix}`);

    registerCompileGammaCommand(context, gammaExecutable);
}
