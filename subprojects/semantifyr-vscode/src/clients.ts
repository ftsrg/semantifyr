/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import vscode, {commands, ExtensionContext, Location, Position, Range, workspace} from "vscode";
import path from "path";
import {executablePostfix} from "./runnerUtils.js";
import * as net from 'net';
import { LanguageClient, LanguageClientOptions, ServerOptions} from "vscode-languageclient/node.js";
import { OxstsTestController } from "./OxstsTestController.js";

export let oxstsClient: LanguageClient;
let xstsClient: LanguageClient;
let gammaClient: LanguageClient;

let oxstsTestController: OxstsTestController;

type NavigateToParams = {
    locations: Location[]
}

export async function startClients(context: ExtensionContext) {
    const oxstsIdeExecutable = path.join(context.extensionPath, 'bin', 'oxsts.lang.ide', 'bin', `oxsts.lang.ide${executablePostfix}`);
    const xstsIdeExecutable = path.join(context.extensionPath, 'bin', 'xsts.lang.ide', 'bin', `xsts.lang.ide${executablePostfix}`);
    const gammaIdeExecutable = path.join(context.extensionPath, 'bin', 'gamma.lang.ide', 'bin', `gamma.lang.ide${executablePostfix}`);

    if (process.env.DEBUG_OXSTS_LSP) {
        const port = Number(process.env.DEBUG_OXSTS_LSP)
        oxstsClient = createRemoteLspClient(port, "oxsts");
    } else {
        oxstsClient = createLspClient(oxstsIdeExecutable, "oxsts");
    }

    if (process.env.DEBUG_XSTS_LSP) {
        const port = Number(process.env.DEBUG_XSTS_LSP)
        xstsClient = createRemoteLspClient(port, "xsts");
    } else {
        xstsClient = createLspClient(xstsIdeExecutable, "xsts");
    }

    if (process.env.DEBUG_GAMMA_LSP) {
        const port = Number(process.env.DEBUG_GAMMA_LSP)
        gammaClient = createRemoteLspClient(port, "gamma");
    } else {
        gammaClient = createLspClient(gammaIdeExecutable, "gamma");
    }


    await oxstsClient.start();
    await xstsClient.start();
    await gammaClient.start();

    oxstsTestController = new OxstsTestController(oxstsClient);
    oxstsTestController.registerTestProvider(context);

    oxstsClient.onNotification('workspace/navigateTo', (params: NavigateToParams) => {
        void navigateToLocation(params.locations);
    });

    async function navigateToLocation(locations: Location[]) {
        const actualLocations = locations.map(location => {
            const uri = vscode.Uri.parse(location.uri.toString());
            const range = new Range(
                new Position(location.range.start.line, location.range.start.character),
                new Position(location.range.end.line, location.range.end.character),
            )
            return new Location(uri, range);
        });
        
        let editor = vscode.window.activeTextEditor;
        if (editor === undefined) {
            const uri = locations[0].uri;
            const doc = await vscode.workspace.openTextDocument(uri);
            editor = await vscode.window.showTextDocument(doc);
        }

        const currentUri = editor.document.uri;
        const currentPosition = editor.selection.start;

        await commands.executeCommand('editor.action.goToLocations', currentUri, currentPosition, actualLocations, "peek");
    }

}

export async function stopClients() {
    await oxstsClient.stop();
    await xstsClient.stop();
    await gammaClient.stop();
}

function createRemoteLspClient(port: number, language: string): LanguageClient {
    const serverOptions: ServerOptions = () => {
        return new Promise((resolve, reject) => {
            const socket = net.connect(port, '127.0.0.1', () => {
                resolve({
                    reader: socket,
                    writer: socket
                });
            });
            socket.on('error', (err) => {
                reject(err);
            });
        });
    };

    const clientOptions: LanguageClientOptions = {
        documentSelector: [{ scheme: 'file', language: language }],
        synchronize: {
            fileEvents: workspace.createFileSystemWatcher(`**`)
        }
    };

    return new LanguageClient(
        `${language}LSP`,
        `${language.toUpperCase()} Language Server`,
        serverOptions,
        clientOptions
    );
}

function createLspClient(lspExecutable: string, language: string): LanguageClient {
    const serverOptions: ServerOptions = {
        run: {command: lspExecutable, options: { shell: true }},
        debug: {command: lspExecutable, options: { shell: true }}
    };

    const clientOptions: LanguageClientOptions = {
        documentSelector: [{scheme: 'file', language: language}],
        synchronize: {
            fileEvents: workspace.createFileSystemWatcher(`**`)
        }
    };

    return new LanguageClient(
        `${language}LSP`,
        `${language.toUpperCase()} Language Server`,
        serverOptions,
        clientOptions
    );
}
