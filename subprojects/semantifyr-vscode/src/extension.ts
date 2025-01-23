/*
 * SPDX-FileCopyrightText: 2023-2024 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import * as os from 'os';
import * as path from 'path';
import * as childProcess from 'child_process';
import { LanguageClient, LanguageClientOptions, ServerOptions } from 'vscode-languageclient/node';
import { workspace, ExtensionContext } from 'vscode';
import * as vscode from "vscode";

let oxstsClient: LanguageClient;
let xstsClient: LanguageClient;
let outputChannel: vscode.OutputChannel;

function writeToOutput(message: string, reveal: boolean = true) {
    outputChannel.append(message);
    if (reveal) {        
        outputChannel.show(true);
    }
}

class OxstsCodeLensProvider implements vscode.CodeLensProvider {

    public provideCodeLenses(document: vscode.TextDocument, token: vscode.CancellationToken): vscode.CodeLens[] {
        const codeLenses: vscode.CodeLens[] = [];
        const targetRegex = /target\s+([a-zA-Z0-9_]+)/g;
        const documentText = document.getText();
        
        let match: RegExpExecArray | null;

        while ((match = targetRegex.exec(documentText)) !== null) {
            const targetName = match[1];
            const line = document.positionAt(match.index).line;
            const range = new vscode.Range(line, 0, line, 0);

            codeLenses.push(new vscode.CodeLens(range, {
                title: 'Compile',
                command: 'semantifyr.compileTarget',
                arguments: [targetName, document]
            }));

            codeLenses.push(new vscode.CodeLens(range, {
                title: 'Verify',
                command: 'semantifyr.verifyTarget',
                arguments: [targetName, document, true]
            }));

            codeLenses.push(new vscode.CodeLens(range, {
                title: 'Verify (no Witness)',
                command: 'semantifyr.verifyTarget',
                arguments: [targetName, document, false]
            }));
        }

        return codeLenses;
    }

}

export function activate(context: ExtensionContext) {
    const runner = os.type() === 'Windows_NT' ? 'cmd' : 'sh';
    const commandArg = os.type() === 'Windows_NT' ? '/c' : '';
    const executablePostfix = os.type() === 'Windows_NT' ? '.bat' : '';

    const oxstsIdeExecutable = path.join(context.extensionPath, 'bin', 'oxsts.lang.ide', 'bin', `oxsts.lang.ide${executablePostfix}`);
    const xstsIdeExecutable = path.join(context.extensionPath, 'bin', 'xsts.lang.ide', 'bin', `xsts.lang.ide${executablePostfix}`);
    const compilerExecutable = path.join(context.extensionPath, 'bin', 'semantifyr', 'bin', `semantifyr${executablePostfix}`);

    // TODO: needs testing on Windows
    function spawnProcess(compilerExecutable: string, semantifyrCommand: string, remainingArgs: string[]) {
        if (os.type() === 'Windows_NT') {
            const args = [commandArg, compilerExecutable, semantifyrCommand].concat(remainingArgs)
            return childProcess.spawn(runner, args);
        } else { // assume linux
            // -c does not work (would probably work if args would be concatenated to a string), but also not necessary
            const args = [compilerExecutable, semantifyrCommand].concat(remainingArgs)
            return childProcess.spawn(runner, args);
        }
    }

    outputChannel = vscode.window.createOutputChannel("Semantifyr");
    context.subscriptions.push(outputChannel);

    context.subscriptions.push(
        vscode.languages.registerCodeLensProvider({ language: 'oxsts' }, new OxstsCodeLensProvider())
    );

    context.subscriptions.push(
        vscode.commands.registerCommand('semantifyr.compileTarget', (targetName: string, document: vscode.TextDocument) => {
            const documentPath = document.uri.fsPath;
            const documentDirectory = path.dirname(documentPath);
            const outputFile = path.join(documentDirectory, `${targetName}.xsts`);
            const workspaceFolders = vscode.workspace.workspaceFolders;
            const workspaceFolder = workspaceFolders ? workspaceFolders[0].uri.fsPath : documentDirectory;

            vscode.window.showInformationMessage(`Compiling target: ${targetName}`);

            vscode.window.withProgress({
                location: vscode.ProgressLocation.Window,
                title: `Compiling target: ${targetName}`,
                cancellable: false
            }, () => {
                return new Promise<void>((resolve, reject) => {
                    const process = spawnProcess(compilerExecutable, 'compile', [documentPath, workspaceFolder, targetName, "-o", outputFile]);

                    outputChannel.clear();

                    process.stdout.on('data', (data) => {
                        writeToOutput(data.toString());
                    });

                    process.stderr.on('data', (data) => {
                        writeToOutput(data.toString());
                    });

                    process.on('close', (code) => {
                        if (code === 0) {
                            vscode.window.showInformationMessage(`Success! Compiled ${targetName} to ${outputFile}`);
                            writeToOutput(`Success! Compiled ${targetName} to ${outputFile}`);
                            resolve();
                        } else {
                            vscode.window.showErrorMessage(`Compilation failed with exit code ${code}`);
                            writeToOutput(`Compilation failed with exit code ${code}`);
                            reject(new Error(`Process exited with code ${code}`));
                        }
                    });

                    process.on('error', (error) => {
                        vscode.window.showErrorMessage(`Error compiling target: ${error.message}`);
                        writeToOutput(`Error: ${error.message}`);
                        reject(error);
                    });
                });
            });            
        })
    );

    context.subscriptions.push(
        vscode.commands.registerCommand('semantifyr.verifyTarget', (targetName: string, document: vscode.TextDocument, generateWitness: boolean) => {
            const documentPath = document.uri.fsPath;
            const documentDirectory = path.dirname(documentPath);
            const workspaceFolders = vscode.workspace.workspaceFolders;
            const workspaceFolder = workspaceFolders ? workspaceFolders[0].uri.fsPath : documentDirectory;

            vscode.window.showInformationMessage(`Verifying target: ${targetName}`);

            vscode.window.withProgress({
                location: vscode.ProgressLocation.Window,
                title: `Verifying target: ${targetName}`,
                cancellable: false
            }, () => {
                return new Promise<void>((resolve, reject) => {
                    const args = [documentPath, workspaceFolder, targetName]

                    if (generateWitness) {
                        args.push("--witness")
                    }

                    const process = spawnProcess(compilerExecutable, 'verify', args);

                    outputChannel.clear();

                    process.stdout.on('data', (data) => {
                        writeToOutput(data.toString());
                    });

                    process.stderr.on('data', (data) => {
                        writeToOutput(data.toString());
                    });

                    process.on('close', (code) => {
                        if (code === 0) {
                            vscode.window.showInformationMessage(`Success! Verified target: ${targetName}`);
                            writeToOutput(`Success! Verified target: ${targetName}`);
                            resolve();
                        } else {
                            vscode.window.showErrorMessage(`Verification failed with exit code ${code}`);
                            writeToOutput(`Verification failed with exit code ${code}`);
                            reject(new Error(`Process exited with code ${code}`));
                        }
                    });

                    process.on('error', (error) => {
                        vscode.window.showErrorMessage(`Error verifying target: ${error.message}`);
                        writeToOutput(`Error: ${error.message}`);
                        reject(error);
                    });
                });
            });
        })
    );

    context.subscriptions.push(
        vscode.commands.registerCommand('semantifyr.verifyXsts', (uri: vscode.Uri) => {
            const documentPath = uri.fsPath;

            vscode.window.showInformationMessage(`Verifying xsts: ${documentPath}`);

            vscode.window.withProgress({
                location: vscode.ProgressLocation.Window,
                title: `Verifying xsts: ${documentPath}`,
                cancellable: false
            }, () => {
                return new Promise<void>((resolve, reject) => {
                    const process = spawnProcess(compilerExecutable, 'verify-xsts', [documentPath]);

                    outputChannel.clear();

                    process.stdout.on('data', (data) => {
                        writeToOutput(data.toString());
                    });

                    process.stderr.on('data', (data) => {
                        writeToOutput(data.toString());
                    });

                    process.on('close', (code) => {
                        if (code === 0) {
                            vscode.window.showInformationMessage(`Success! Verified xsts: ${documentPath}`);
                            writeToOutput(`Success! Verified xsts: ${documentPath}`);
                            resolve();
                        } else {
                            vscode.window.showErrorMessage(`Verification failed with exit code ${code}`);
                            writeToOutput(`Verification failed with exit code ${code}`);
                            reject(new Error(`Process exited with code ${code}`));
                        }
                    });

                    process.on('error', (error) => {
                        vscode.window.showErrorMessage(`Error verifying target: ${error.message}`);
                        writeToOutput(`Error: ${error.message}`);
                        reject(error);
                    });
                });
            });
        })
    );
    
    let oxstsServerOptions: ServerOptions = {
        run : { command: runner, args: [ commandArg, oxstsIdeExecutable ] },
        debug: { command: runner, args: [ commandArg, oxstsIdeExecutable ] }
    };

    const oxstsClientOptions: LanguageClientOptions = {
        documentSelector: [{ scheme: 'file', language: 'oxsts' }],
        synchronize: {
            fileEvents: workspace.createFileSystemWatcher('**/.oxsts')
        }
    };

    oxstsClient = new LanguageClient(
            'oxstsLSP',
            'OXSTS Language Server',
            oxstsServerOptions,
            oxstsClientOptions
    );


    let xstsServerOptions: ServerOptions = {
        run : { command: runner, args: [ commandArg, xstsIdeExecutable ] },
        debug: { command: runner, args: [ commandArg, xstsIdeExecutable ] }
    };

    const xstsClientOptions: LanguageClientOptions = {
        documentSelector: [{ scheme: 'file', language: 'xsts' }],
        synchronize: {
            fileEvents: workspace.createFileSystemWatcher('**/.xsts')
        }
    };

    xstsClient = new LanguageClient(
            'xstsLSP',
            'XSTS Language Server',
            xstsServerOptions,
            xstsClientOptions
    );

    oxstsClient.start();
    xstsClient.start();
}

export function deactivate() {
    return oxstsClient.stop().then(() => {
        xstsClient.stop();
    });
}
