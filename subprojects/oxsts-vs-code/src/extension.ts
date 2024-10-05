/*
 * SPDX-FileCopyrightText: 2023-2024 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import * as path from 'path';
import { LanguageClient, LanguageClientOptions, ServerOptions } from 'vscode-languageclient/node';
import { workspace, ExtensionContext } from 'vscode';
import { Trace } from 'vscode-jsonrpc';

let client: LanguageClient;

export function activate(context: ExtensionContext) {
    const serverModule = path.join(context.extensionPath, 'bin', 'oxsts.lang.ide', 'bin', 'oxsts.lang.ide.bat');
    
    let serverOptions: ServerOptions = {
        run : { command: 'cmd', args: ['/c', serverModule] },
        debug: { command: 'cmd', args: ['/c', serverModule], options: { env: createDebugEnv() } }
    };

    const clientOptions: LanguageClientOptions = {
        documentSelector: [{ scheme: 'file', language: 'oxsts' }],
        synchronize: {
            fileEvents: workspace.createFileSystemWatcher('**/.oxsts')
        }
    };

    client = new LanguageClient(
            'oxstsLSP',
            'OXSTS Language Server',
            serverOptions,
            clientOptions
    );

    client.setTrace(Trace.Verbose);
    client.start();
}

export function deactivate() {
    if (!client) {
        return undefined;
    }
    return client.stop();
}

function createDebugEnv() {
    return Object.assign({
        JAVA_OPTS:"-Xdebug -Xrunjdwp:server=y,transport=dt_socket,address=8000,suspend=n,quiet=y"
    }, process.env);
}
