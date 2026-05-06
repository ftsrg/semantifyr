/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import vscode, { CancellationToken, ExtensionContext, Position, Range, RelativePattern, TestController, TestItem, TestMessage, TestRunProfileKind, TestRunRequest, TextDocument, Uri, WorkspaceFolder, WorkspaceFoldersChangeEvent } from "vscode";
import { LanguageClient } from "vscode-languageclient/node.js";
import { ExecuteCommandRequest } from "vscode-languageclient";
import {
    VerificationStatus,
    type VerificationCaseRequest,
    type VerificationCaseResult,
    type VerificationCaseSpecification,
} from "@semantifyr/editor-common";

export interface VerificationTestControllerConfig {
    languageId: string;
    fileGlob: string;
    controllerId: string;
    controllerLabel: string;
    discoverCommand: string;
    verifyCommand: string;
}

export class VerificationTestController {

    protected readonly client: LanguageClient;
    protected readonly config: VerificationTestControllerConfig;
    protected controller!: TestController;

    constructor(client: LanguageClient, config: VerificationTestControllerConfig) {
        this.client = client;
        this.config = config;
    }

    registerTestProvider(context: ExtensionContext) {
        this.controller = vscode.tests.createTestController(this.config.controllerId, this.config.controllerLabel);
        context.subscriptions.push(this.controller);

        this.controller.resolveHandler = async (item) => {
            if (!item) {
                await this.refreshWorkspaceTests();
            } else if (item.uri) {
                const doc = await vscode.workspace.openTextDocument(item.uri);
                await this.refreshDocumentTests(doc);
            }
        };
        this.controller.refreshHandler = async () => {
            await this.refreshWorkspaceTests();
        };

        const runProfile = this.controller.createRunProfile(
            'Run',
            TestRunProfileKind.Run,
            async (request, token) => await this.runTests(request, token),
            true
        );
        context.subscriptions.push(runProfile);

        const watcher = vscode.workspace.createFileSystemWatcher(this.config.fileGlob);
        watcher.onDidChange(async (uri) => await this.refreshDocumentUriTests(uri));
        watcher.onDidCreate(async (uri) => await this.refreshDocumentUriTests(uri));
        watcher.onDidDelete((uri) => this.controller.items.delete(uri.toString()));

        context.subscriptions.push(
            vscode.workspace.onDidChangeWorkspaceFolders(async (event) => await this.updateWorkspaceTests(event)),
            vscode.workspace.onDidOpenTextDocument(async (document) => await this.refreshDocumentTests(document)),
            vscode.workspace.onDidRenameFiles(async (event) => {
                for (const file of event.files) {
                    this.controller.items.delete(file.oldUri.toString());
                    await this.refreshDocumentUriTests(file.newUri);
                }
            }),
        );
    }

    public async refreshWorkspaceTests() {
        const uris = await vscode.workspace.findFiles(this.config.fileGlob);
        await this.refreshDocumentsUri(uris);
    }

    protected async updateWorkspaceTests(changeEvent: WorkspaceFoldersChangeEvent) {
        for (const removed of changeEvent.removed) {
            await this.forgetFolderTests(removed);
        }
        for (const added of changeEvent.added) {
            await this.discoverFolderTests(added);
        }
    }

    protected async forgetFolderTests(folder: WorkspaceFolder) {
        const uris = await vscode.workspace.findFiles(new RelativePattern(folder, this.config.fileGlob));
        this.forgetDocumentsUri(uris);
    }

    protected async discoverFolderTests(folder: WorkspaceFolder) {
        const uris = await vscode.workspace.findFiles(new RelativePattern(folder, this.config.fileGlob));
        await this.refreshDocumentsUri(uris);
    }

    protected forgetDocumentsUri(uris: readonly Uri[]) {
        for (const uri of uris) {
            this.controller.items.delete(uri.toString());
        }
    }

    protected async refreshDocumentsUri(uris: readonly Uri[]) {
        for (const uri of uris) {
            await this.refreshDocumentUriTests(uri);
        }
    }

    protected async refreshDocumentTests(doc: TextDocument) {
        if (doc.languageId !== this.config.languageId) {
            return;
        }
        await this.refreshDocumentUriTests(doc.uri);
    }

    protected async refreshDocumentUriTests(uri: Uri) {
        const documentTestItem = this.getDocumentTestItem(uri);

        const verificationCases = await this.client.sendRequest(ExecuteCommandRequest.type, {
            command: this.config.discoverCommand,
            arguments: [uri.toString()]
        }) as VerificationCaseSpecification[];

        const seen = new Set<string>();

        for (const verificationCase of verificationCases) {
            seen.add(verificationCase.id);
            const caseTestItem = this.getCaseTestItem(documentTestItem, verificationCase);
            caseTestItem.range = new Range(
                new Position(verificationCase.location.range.start.line, verificationCase.location.range.start.character),
                new Position(verificationCase.location.range.end.line, verificationCase.location.range.end.character)
            );
        }

        for (const [id] of documentTestItem.children) {
            if (!seen.has(id)) {
                documentTestItem.children.delete(id);
            }
        }

        if (documentTestItem.children.size === 0) {
            this.controller.items.delete(uri.toString());
        }
    }

    protected getDocumentTestItem(uri: Uri): TestItem {
        let fileItem = this.controller.items.get(uri.toString());
        if (!fileItem) {
            fileItem = this.controller.createTestItem(uri.toString(), uri.path.split('/').pop() || uri.toString(), uri);
            fileItem.range = new Range(new Position(0, 0), new Position(0, 0));
            fileItem.canResolveChildren = true;
            this.controller.items.add(fileItem);
        }
        return fileItem;
    }

    protected getCaseTestItem(documentTestItem: TestItem, verificationCase: VerificationCaseSpecification): TestItem {
        let testItem = documentTestItem.children.get(verificationCase.id);
        if (!testItem) {
            testItem = this.controller.createTestItem(verificationCase.id, verificationCase.label, documentTestItem.uri);
            documentTestItem.children.add(testItem);
        }
        return testItem;
    }

    protected async runTests(request: TestRunRequest, token: CancellationToken) {
        const run = this.controller.createTestRun(request);
        const testItems: TestItem[] = [];

        const add = (testItem: TestItem) => {
            if (request.exclude?.includes(testItem)) {
                return;
            }
            if (testItem.children.size === 0) {
                run.enqueued(testItem);
                testItems.push(testItem);
            } else {
                testItem.children.forEach(add);
            }
        };

        if (request.include) {
            request.include.forEach(add);
        } else {
            this.controller.items.forEach(add);
        }

        for (const testItem of testItems) {
            if (token.isCancellationRequested) {
                break;
            }

            run.started(testItem);

            try {
                const range = testItem.range!;
                const request: VerificationCaseRequest = {
                    uri: testItem.uri!.toString(),
                    range: {
                        start: { line: range.start.line, character: range.start.character },
                        end: { line: range.end.line, character: range.end.character },
                    },
                };
                const result = await this.client.sendRequest(ExecuteCommandRequest.type, {
                    command: this.config.verifyCommand,
                    arguments: [request],
                }, token) as VerificationCaseResult;

                switch (result.status) {
                    case VerificationStatus.Passed:
                        run.passed(testItem);
                        break;
                    case VerificationStatus.Failed:
                        run.failed(testItem, new TestMessage(result.message ?? 'Failed'));
                        break;
                    case VerificationStatus.Inconclusive:
                        run.errored(testItem, new TestMessage(`Inconclusive: ${result.message ?? 'no message'}`));
                        break;
                    case VerificationStatus.NotSupported:
                        run.errored(testItem, new TestMessage(`Not supported by the chosen portfolio: ${result.message ?? 'no message'}`));
                        break;
                    case VerificationStatus.Errored:
                    default:
                        run.errored(testItem, new TestMessage(result.message ?? 'Test run failed'));
                        break;
                }
            } catch (e: unknown) {
                run.errored(testItem, new TestMessage(e instanceof Error ? e.message : 'Test run failed'));
            }
        }

        run.end();
    }
}
