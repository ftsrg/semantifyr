/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import vscode, {ExtensionContext} from "vscode";
import path from "path";
import childProcess from "child_process";
import {outputChannel, writeErrorMessage, writeSuccessMessage, writeToOutputChannel} from "./outputChannel";
import {commandArg, executablePostfix, runnerUtils} from "./runnerUtils";

export async function executeProcess<R>(name: string, process: () => Thenable<R>) {
    vscode.window.showInformationMessage(name);
    await vscode.window.withProgress(
            {
                location: vscode.ProgressLocation.Window,
                title: name,
                cancellable: false
            },
            () => process()
    );
}

function registerCompileTargetCommand(context: ExtensionContext, runner: string, commandArg: string, compilerExecutable: string) {
    context.subscriptions.push(
            vscode.commands.registerCommand('semantifyr.compileTarget', async (targetName: string, document: vscode.TextDocument) => {
                const documentPath = document.uri.fsPath;
                const documentDirectory = path.dirname(documentPath);
                const outputFile = path.join(documentDirectory, `${targetName}.xsts`);
                const workspaceFolders = vscode.workspace.workspaceFolders;
                const workspaceFolder = workspaceFolders ? workspaceFolders[0].uri.fsPath : documentDirectory;

                await executeProcess(`Compiling target: ${targetName}`, () => {
                    return new Promise<void>((resolve, reject) => {
                        const process = childProcess.spawn(runner, [commandArg, compilerExecutable, 'compile', documentPath, workspaceFolder, targetName, '-o', outputFile]);

                        outputChannel.clear();

                        process.stdout.on('data', (data) => {
                            writeToOutputChannel(data.toString());
                        });

                        process.stderr.on('data', (data) => {
                            writeToOutputChannel(data.toString());
                        });

                        process.on('close', (code) => {
                            if (code === 0) {
                                writeSuccessMessage(`Success! Compiled ${targetName} to ${outputFile}`);
                                resolve();
                            } else {
                                writeErrorMessage(`Compilation failed with exit code ${code}`);
                                reject(new Error(`Process exited with code ${code}`));
                            }
                        });

                        process.on('error', (error) => {
                            writeErrorMessage(`Error compiling target: ${error.message}`);
                            reject(error);
                        });
                    });
                });
            })
    );
}

function registerVerifyTargetCommand(context: ExtensionContext, runner: string, commandArg: string, compilerExecutable: string) {
    context.subscriptions.push(
            vscode.commands.registerCommand('semantifyr.verifyTarget', async (targetName: string, document: vscode.TextDocument, generateWitness: boolean) => {
                const documentPath = document.uri.fsPath;
                const documentDirectory = path.dirname(documentPath);
                const workspaceFolders = vscode.workspace.workspaceFolders;
                const workspaceFolder = workspaceFolders ? workspaceFolders[0].uri.fsPath : documentDirectory;

                await executeProcess(`Verifying target: ${targetName}`, () => {
                    return new Promise<void>((resolve, reject) => {
                        const args = [commandArg, compilerExecutable, 'verify', documentPath, workspaceFolder, targetName];
                        if (generateWitness) {
                            args.push("--witness");
                        }

                        const process = childProcess.spawn(runner, args);

                        outputChannel.clear();

                        process.stdout.on('data', (data) => {
                            writeToOutputChannel(data.toString());
                        });

                        process.stderr.on('data', (data) => {
                            writeToOutputChannel(data.toString());
                        });

                        process.on('close', (code) => {
                            if (code === 0) {
                                writeSuccessMessage(`Success! Verified target: ${targetName}`);
                                resolve();
                            } else {
                                writeErrorMessage(`Verification failed with exit code ${code}`);
                                reject(new Error(`Process exited with code ${code}`));
                            }
                        });

                        process.on('error', (error) => {
                            writeErrorMessage(`Error verifying target: ${error.message}`);
                            reject(error);
                        });
                    });
                });
            })
    );
}

function registerVerifyXstsCommand(context: ExtensionContext, runner: string, commandArg: string, compilerExecutable: string) {
    context.subscriptions.push(
            vscode.commands.registerCommand('semantifyr.verifyXsts', async (uri: vscode.Uri) => {
                const documentPath = uri.fsPath;

                await executeProcess(`Verifying XSTS model: ${documentPath}`, () => {
                    return new Promise<void>((resolve, reject) => {
                        const process = childProcess.spawn(runner, [commandArg, compilerExecutable, 'verify-xsts', documentPath]);

                        outputChannel.clear();

                        process.stdout.on('data', (data) => {
                            writeToOutputChannel(data.toString());
                        });

                        process.stderr.on('data', (data) => {
                            writeToOutputChannel(data.toString());
                        });

                        process.on('close', (code) => {
                            if (code === 0) {
                                writeSuccessMessage(`Success! Verified XSTS model: ${documentPath}`);
                                resolve();
                            } else {
                                writeErrorMessage(`Verification failed with exit code ${code}`);
                                reject(new Error(`Process exited with code ${code}`));
                            }
                        });

                        process.on('error', (error) => {
                            writeErrorMessage(`Error verifying XSTS model: ${error.message}`);
                            reject(error);
                        });
                    });
                });
            })
    );
}


function registerCompileGammaCommand(context: ExtensionContext, runner: string, commandArg: string, gammaExecutable: string) {
    context.subscriptions.push(
            vscode.commands.registerCommand('gamma.compile', async (uri: vscode.Uri) => {
                const documentPath = uri.fsPath;

                await executeProcess(`Compiling Gamma model: ${documentPath}.`, () => {
                    return new Promise<void>((resolve, reject) => {
                        const process = childProcess.spawn(runner, [commandArg, gammaExecutable, 'compile', documentPath]);

                        outputChannel.clear();

                        process.stdout.on('data', (data) => {
                            writeToOutputChannel(data.toString());
                        });

                        process.stderr.on('data', (data) => {
                            writeToOutputChannel(data.toString());
                        });

                        process.on('close', (code) => {
                            if (code === 0) {
                                writeSuccessMessage(`Success! Gamma model: ${documentPath}.`);
                                resolve();
                            } else {
                                writeErrorMessage(`Compilation failed with exit code ${code}.`);
                                reject(new Error(`Process exited with code ${code}`));
                            }
                        });

                        process.on('error', (error) => {
                            writeErrorMessage(`Error compiling target: ${error.message}.`);
                            reject(error);
                        });
                    });
                });
            })
    );
}

function registerVerifyGammaCommand(context: ExtensionContext, runner: string, commandArg: string, gammaExecutable: string) {
    context.subscriptions.push(
            vscode.commands.registerCommand('gamma.verify', async (verificationCase: string, document: vscode.TextDocument, generateWitness: boolean) => {
                const documentPath = document.uri.fsPath;
                const documentDirectory = path.dirname(documentPath);
                const workspaceFolders = vscode.workspace.workspaceFolders;
                const workspaceFolder = workspaceFolders ? workspaceFolders[0].uri.fsPath : documentDirectory;

                await executeProcess(`Verifying verification case: ${verificationCase}`, () => {
                    return new Promise<void>((resolve, reject) => {
                        const args = [commandArg, gammaExecutable, 'verify', documentPath, verificationCase, workspaceFolder];
                        if (generateWitness) {
                            args.push("--witness");
                        }

                        const process = childProcess.spawn(runner, args);

                        outputChannel.clear();

                        process.stdout.on('data', (data) => {
                            writeToOutputChannel(data.toString());
                        });

                        process.stderr.on('data', (data) => {
                            writeToOutputChannel(data.toString());
                        });

                        process.on('close', (code) => {
                            if (code === 0) {
                                writeSuccessMessage(`Success! Verified verification case: ${verificationCase}`);
                                resolve();
                            } else {
                                writeErrorMessage(`Verification failed with exit code ${code}`);
                                reject(new Error(`Process exited with code ${code}`));
                            }
                        });

                        process.on('error', (error) => {
                            writeErrorMessage(`Error verifying verification case: ${error.message}`);
                            reject(error);
                        });
                    });
                });
            })
    );
}

export function registerCommands(context: ExtensionContext) {
    const compilerExecutable = path.join(context.extensionPath, 'bin', 'semantifyr', 'bin', `semantifyr${executablePostfix}`);
    const gammaExecutable = path.join(context.extensionPath, 'bin', 'gamma-frontend', 'bin', `gamma-frontend${executablePostfix}`);

    registerCompileTargetCommand(context, runnerUtils, commandArg, compilerExecutable);
    registerVerifyTargetCommand(context, runnerUtils, commandArg, compilerExecutable);
    registerCompileGammaCommand(context, runnerUtils, commandArg, gammaExecutable);
    registerVerifyGammaCommand(context, runnerUtils, commandArg, gammaExecutable);
    registerVerifyXstsCommand(context, runnerUtils, commandArg, compilerExecutable);
}
