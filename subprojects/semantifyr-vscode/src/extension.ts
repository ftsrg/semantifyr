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

class SemantifyrCodeLensProvider implements vscode.CodeLensProvider {

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
                arguments: [targetName, document]
            }));
        }

        return codeLenses;
    }
}

export function activate(context: ExtensionContext) {
    const oxstsIdeExecutable = path.join(context.extensionPath, 'bin', 'oxsts.lang.ide', 'bin', 'oxsts.lang.ide.bat');
    const xstsIdeExecutable = path.join(context.extensionPath, 'bin', 'xsts.lang.ide', 'bin', 'xsts.lang.ide.bat');
    const compilerExecutable = path.join(context.extensionPath, 'bin', 'compiler', 'bin', 'compiler.bat');

    const runner = os.type() === 'Windows_NT' ? 'cmd' : 'sh';
    const commandArg = os.type() === 'Windows_NT' ? '/c' : '-c';


    context.subscriptions.push(
        vscode.languages.registerCodeLensProvider({ language: 'oxsts' }, new SemantifyrCodeLensProvider())
    );

    context.subscriptions.push(
        vscode.commands.registerCommand('semantifyr.compileTarget', (targetName: string, document: vscode.TextDocument) => {
            const documentPath = document.uri.fsPath;
            const documentDirectory = path.dirname(documentPath);
            const outputFile = path.join(documentDirectory, `${targetName}.xsts`);
            const workspaceFolders = vscode.workspace.workspaceFolders;
            const workspaceFolder = workspaceFolders ? workspaceFolders[0].uri.fsPath : documentDirectory;

            vscode.window.showInformationMessage(`Compiling target: ${targetName}`);

            const compileCommand = `${runner} ${commandArg} ${compilerExecutable} ${documentPath} ${workspaceFolder} ${targetName} -o ${outputFile}`;

            vscode.window.withProgress({
                location: vscode.ProgressLocation.Window,
                title: `Compiling target: ${targetName}`,
                cancellable: false
            }, () => {
                return new Promise<void>((resolve, reject) => {                    
                    childProcess.exec(compileCommand, (error, stderr) => {
                        if (error) {
                            vscode.window.showErrorMessage(`Error compiling target: ${error.message}`);
                            reject(error);
                        } else if (stderr) {
                            vscode.window.showErrorMessage(`Compilation error: ${stderr}`);
                            reject(stderr);
                        } else {
                            vscode.window.showInformationMessage(`Success! See ${targetName}.xsts`);
                            resolve();
                        }
                    });
                });
            });            
        })
    );

    context.subscriptions.push(
        vscode.commands.registerCommand('semantifyr.verifyTarget', (targetName: string, document: vscode.TextDocument) => {
            vscode.window.showInformationMessage(`Verification is not yet supported from VS Code!`);
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
