/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import {LanguageClient, LanguageClientOptions, ServerOptions} from "vscode-languageclient/node";
import {ExtensionContext, workspace} from "vscode";
import path from "path";
import {commandArg, executablePostfix, runnerUtils} from "./runnerUtils";
import * as net from 'net';

let oxstsClient: LanguageClient;
let xstsClient: LanguageClient;
let gammaClient: LanguageClient;
let cexClient: LanguageClient;

export async function startClients(context: ExtensionContext) {
    const oxstsIdeExecutable = path.join(context.extensionPath, 'bin', 'oxsts.lang.ide', 'bin', `oxsts.lang.ide${executablePostfix}`);
    const xstsIdeExecutable = path.join(context.extensionPath, 'bin', 'xsts.lang.ide', 'bin', `xsts.lang.ide${executablePostfix}`);
    const cexIdeExecutable = path.join(context.extensionPath, 'bin', 'cex.lang.ide', 'bin', `cex.lang.ide${executablePostfix}`);
    const gammaIdeExecutable = path.join(context.extensionPath, 'bin', 'gamma.lang.ide', 'bin', `gamma.lang.ide${executablePostfix}`);

    if (process.env.DEBUG_OXSTS_LSP) {
        const port = Number(process.env.DEBUG_OXSTS_LSP)
        console.log('Debug mode enabled via launch args for OXSTS');
        oxstsClient = createRemoteLspClient(port, "oxsts");
    } else {
        oxstsClient = createLspClient(runnerUtils, commandArg, oxstsIdeExecutable, "oxsts");
    }

    xstsClient = createLspClient(runnerUtils, commandArg, xstsIdeExecutable, "xsts");
    cexClient = createLspClient(runnerUtils, commandArg, cexIdeExecutable, "cex");
    gammaClient = createLspClient(runnerUtils, commandArg, gammaIdeExecutable, "gamma");

    await oxstsClient.start();
    await xstsClient.start();
    await cexClient.start();
    await gammaClient.start();
}

export async function stopClients() {
    await oxstsClient.stop();
    await xstsClient.stop();
    await cexClient.stop();
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
            fileEvents: workspace.createFileSystemWatcher(`**/*.${language}`)
        }
    };

    return new LanguageClient(
        `${language}LSP`,
        `${language.toUpperCase()} Language Server`,
        serverOptions,
        clientOptions
    );
}

function createLspClient(runner: string, commandArg: string, oxstsIdeExecutable: string, language: string): LanguageClient {
    let serverOptions: ServerOptions = {
        run: {command: runner, args: [commandArg, oxstsIdeExecutable]},
        debug: {command: runner, args: [commandArg, oxstsIdeExecutable]}
    };

    const clientOptions: LanguageClientOptions = {
        documentSelector: [{scheme: 'file', language: language}],
        synchronize: {
            fileEvents: workspace.createFileSystemWatcher(`**/.${language}`)
        }
    };

    return new LanguageClient(
            `${language}LSP`,
            `${language.toUpperCase()} Language Server`,
            serverOptions,
            clientOptions
    );
}
