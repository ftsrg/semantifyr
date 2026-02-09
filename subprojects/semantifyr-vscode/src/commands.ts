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

function registerCompileTargetCommand(context: ExtensionContext, compilerExecutable: string) {
    context.subscriptions.push(
        vscode.commands.registerCommand('semantifyr.compileTarget', async (targetName: string, document: vscode.TextDocument) => {
            const documentPath = document.uri.fsPath;
            const documentDirectory = path.dirname(documentPath);
            const outputFile = path.join(documentDirectory, `${targetName}.xsts`);
            const workspaceFolders = vscode.workspace.workspaceFolders;
            const workspaceFolder = workspaceFolders ? workspaceFolders[0].uri.fsPath : documentDirectory;
            const args = `${compilerExecutable} compile ${documentPath} ${workspaceFolder} ${targetName} -o ${outputFile}`;

            await waitForProcess(
                childProcess.exec(args),
                `Compiling target: ${targetName}`,
                `Success! Compiled ${targetName} to ${outputFile}`, 
                (error) => `Error compiling target: ${error.message}.`
            )
        })
    );
}

function registerVerifyTargetCommand(context: ExtensionContext, compilerExecutable: string) {
    context.subscriptions.push(
        vscode.commands.registerCommand('semantifyr.verifyTarget', async (targetName: string, document: vscode.TextDocument, generateWitness: boolean) => {
            const documentPath = document.uri.fsPath;
            const documentDirectory = path.dirname(documentPath);
            const workspaceFolders = vscode.workspace.workspaceFolders;
            const workspaceFolder = workspaceFolders ? workspaceFolders[0].uri.fsPath : documentDirectory;
            
            let args = `${compilerExecutable} verify ${documentPath} ${workspaceFolder} ${targetName}`;
            if (generateWitness) {
                args += " --witness";
            }

            await waitForProcess(
                childProcess.exec(args), 
                `Verifying target: ${targetName}`, 
                `Success! Verified target: ${targetName}.`,
                (error) => `Error verifying target: ${error.message}.`
            );
        })
    );
}

function registerVerifyXstsCommand(context: ExtensionContext, compilerExecutable: string) {
    context.subscriptions.push(
        vscode.commands.registerCommand('semantifyr.verifyXsts', async (uri: vscode.Uri) => {
            const documentPath = uri.fsPath;
            const args = `${compilerExecutable} verify-xsts ${documentPath}`;

            await waitForProcess(
                childProcess.exec(args), 
                `Verifying XSTS model: ${documentPath}`, 
                `Success! Verified XSTS model: ${documentPath}.`,
                (error) => `Error verifying XSTS model: ${error.message}.`
            );
        })
    );
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

function registerVerifyGammaCommand(context: ExtensionContext, gammaExecutable: string) {
    context.subscriptions.push(
        vscode.commands.registerCommand('gamma.verify', async (verificationCase: string, document: vscode.TextDocument, generateWitness: boolean) => {
            const documentPath = document.uri.fsPath;
            const documentDirectory = path.dirname(documentPath);
            const workspaceFolders = vscode.workspace.workspaceFolders;
            const workspaceFolder = workspaceFolders ? workspaceFolders[0].uri.fsPath : documentDirectory;

            let args = `${gammaExecutable} verify ${documentPath} ${verificationCase} ${workspaceFolder}`;
            if (generateWitness) {
                args += " --witness";
            }

            await waitForProcess(
                childProcess.exec(args), 
                `Verifying verification case: ${verificationCase}`, 
                `Success! Verified verification case: ${verificationCase}.`,
                (error) => `Error verifying verification case: ${error.message}.`
            );
        })
    );
}

export function registerCommands(context: ExtensionContext) {
    const compilerExecutable = path.join(context.extensionPath, 'bin', 'semantifyr', 'bin', `semantifyr-cli${executablePostfix}`);
    const gammaExecutable = path.join(context.extensionPath, 'bin', 'gamma-cli', 'bin', `gamma-cli${executablePostfix}`);

    registerCompileTargetCommand(context, compilerExecutable);
    registerVerifyTargetCommand(context, compilerExecutable);
    registerCompileGammaCommand(context, gammaExecutable);
    registerVerifyGammaCommand(context, gammaExecutable);
    registerVerifyXstsCommand(context, compilerExecutable);
}
