/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import {ExtensionContext, workspace} from "vscode";
import path from "path";
import {executablePostfix} from "./runnerUtils.js";
import * as net from 'net';
import {LanguageClient, LanguageClientOptions, ServerOptions} from "vscode-languageclient/node.js";

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
        oxstsClient = createLspClient(oxstsIdeExecutable, "oxsts");
    }

    xstsClient = createLspClient(xstsIdeExecutable, "xsts");
    cexClient = createLspClient(cexIdeExecutable, "cex");
    gammaClient = createLspClient(gammaIdeExecutable, "gamma");

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

function createLspClient(oxstsIdeExecutable: string, language: string): LanguageClient {
    const serverOptions: ServerOptions = {
        run: {command: oxstsIdeExecutable, options: { shell: true }},
        debug: {command: oxstsIdeExecutable, options: { shell: true }}
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
