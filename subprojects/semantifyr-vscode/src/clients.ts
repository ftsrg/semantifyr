/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import vscode, {ExtensionContext, Position, Range, TestController, workspace} from "vscode";
import path from "path";
import {executablePostfix} from "./runnerUtils.js";
import * as net from 'net';
import { ExecuteCommandRequest, LanguageClient, LanguageClientOptions, ServerOptions} from "vscode-languageclient/node.js";

let oxstsClient: LanguageClient;
let xstsClient: LanguageClient;
let gammaClient: LanguageClient;
// let cexClient: LanguageClient;

export async function startClients(context: ExtensionContext) {
    const oxstsIdeExecutable = path.join(context.extensionPath, 'bin', 'oxsts.lang.ide', 'bin', `oxsts.lang.ide${executablePostfix}`);
    const xstsIdeExecutable = path.join(context.extensionPath, 'bin', 'xsts.lang.ide', 'bin', `xsts.lang.ide${executablePostfix}`);
    // const cexIdeExecutable = path.join(context.extensionPath, 'bin', 'cex.lang.ide', 'bin', `cex.lang.ide${executablePostfix}`);
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

    // cexClient = createLspClient(cexIdeExecutable, "cex");
    gammaClient = createLspClient(gammaIdeExecutable, "gamma");

    registerTestProvider(context);

    await oxstsClient.start();
    await xstsClient.start();
    // await cexClient.start();
    await gammaClient.start();
}

let controller: TestController

function registerTestProvider(context: ExtensionContext) {
    controller = vscode.tests.createTestController('oxsts.verification', 'Semantifyr Verification Cases')
    context.subscriptions.push(controller)

    // Discover tests when needed
    controller.resolveHandler = async (item) => {
        if (!item) {
            for (const doc of vscode.workspace.textDocuments) {
                await discoverTestsInDocument(doc)
            }
        } else if (item.uri) {
            const doc = await vscode.workspace.openTextDocument(item.uri)
            await discoverTestsInDocument(doc)
        }
    }

    // Run profile adds the gutter run triangle and Test Explorer actions
    const runProfile = controller.createRunProfile(
        'Run',
        vscode.TestRunProfileKind.Run,
        async (request, token) => await runTests(request, token),
        true // isDefault
    )
    context.subscriptions.push(runProfile)

    // Keep tests in sync as files open/change
    context.subscriptions.push(
        vscode.workspace.onDidChangeWorkspaceFolders(d => discoverTestsInFolder(d)),
        vscode.workspace.onDidOpenTextDocument(d => discoverTestsInDocument(d)),
        vscode.workspace.onDidChangeTextDocument(e => discoverTestsInDocument(e.document))
    )
}

type VerificationCaseSpecification = {
    id: string
    label: string
    range: vscode.Range
}

// eslint-disable-next-line @typescript-eslint/no-unused-vars
function discoverTestsInFolder(changeEvent: vscode.WorkspaceFoldersChangeEvent) {

}

async function discoverTestsInDocument(doc: vscode.TextDocument) {
    if (doc.languageId !== 'oxsts') {
        return;
    }

    const documentTestItem = getOrCreateDocumentTestItem(doc.uri);

    const args = {
        command: 'oxsts.case.discover',
        arguments: [doc.uri.toString()]
    };
    const verificationCases = await oxstsClient.sendRequest(ExecuteCommandRequest.type, args) as VerificationCaseSpecification[];

    const seen = new Set<string>();

    for (const verificationCase of verificationCases) {
        seen.add(verificationCase.id);
        let testItem = documentTestItem.children.get(verificationCase.id);
        if (!testItem) {
            testItem = controller.createTestItem(verificationCase.id, verificationCase.label, doc.uri);
            documentTestItem.children.add(testItem);
        }
        testItem.range = new vscode.Range(
            new vscode.Position(verificationCase.range.start.line, verificationCase.range.start.character),
            new vscode.Position(verificationCase.range.end.line, verificationCase.range.end.character)
        );
    }

    for (const [id] of documentTestItem.children) {
        if (!seen.has(id)) {
            documentTestItem.children.delete(id);
        }
    }
}

function getOrCreateDocumentTestItem(uri: vscode.Uri): vscode.TestItem {
    let fileItem = controller.items.get(uri.toString());
    if (!fileItem) {
        fileItem = controller.createTestItem(uri.toString(), uri.path.split('/').pop() || uri.toString(), uri);
        fileItem.range = new Range(new Position(0, 0), new Position(0, 0));
        controller.items.add(fileItem);
    }
    return fileItem;
}

type VerificationCaseRunResult = { 
    status: 'passed' | 'failed', 
    message?: string
};

async function runTests(request: vscode.TestRunRequest, token: vscode.CancellationToken) {
    const run = controller.createTestRun(request);
    const testItems: vscode.TestItem[] = [];

    const add = (testItem: vscode.TestItem) => {
        if (request.exclude?.includes(testItem)) {
            return;
        }
        if (testItem.children.size === 0) {
            run.enqueued(testItem);
            testItems.push(testItem);
        } else { 
            testItem.children.forEach(add);
        }
    }

    if (request.include) {
        request.include.forEach(add);
    } else {
        controller.items.forEach(add);
    }

    for (const testItem of testItems) {
        if (token.isCancellationRequested) {
            break;
        }

        run.started(testItem);
        
        try {
            const args = {
                command: 'oxsts.case.verify',
                arguments: [testItem.uri!.toString(), testItem.id]
            };
            const result = await oxstsClient.sendRequest(ExecuteCommandRequest.type, args) as VerificationCaseRunResult;

            if (result.status === 'passed') {
                run.passed(testItem);
            } else {
                run.failed(testItem, new vscode.TestMessage(result.message ?? 'Failed'));
            }
        // eslint-disable-next-line @typescript-eslint/no-unused-vars
        } catch (e: unknown) {
            run.errored(testItem, new vscode.TestMessage("Test run failed"));
        }
    }

    run.end()
}

export async function stopClients() {
    await oxstsClient.stop();
    await xstsClient.stop();
    // await cexClient.stop();
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
