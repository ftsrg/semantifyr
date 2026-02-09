
import vscode, { CancellationToken, commands, ExtensionContext, Location, Position, Range, RelativePattern, TestController, TestItem, TestMessage, TestRunProfileKind, TestRunRequest, TextDocument, Uri, WorkspaceFolder, WorkspaceFoldersChangeEvent } from "vscode";
import path from "path";
import { LanguageClient} from "vscode-languageclient/node.js";
import { createRemoteLspClient, createLspClient } from "./client-utils.js";
import { executablePostfix } from "../runner-utils.js";
import { ExecuteCommandRequest } from "vscode-languageclient";

type NavigateToParams = {
    locations: Location[]
}

let client: LanguageClient;

export async function startOxstsClient(context: ExtensionContext) {
    const oxstsIdeExecutable = path.join(context.extensionPath, 'bin', 'oxsts.lang.ide', 'bin', `oxsts.lang.ide${executablePostfix}`);

    if (process.env.DEBUG_OXSTS_LSP) {
        const port = Number(process.env.DEBUG_OXSTS_LSP)
        client = createRemoteLspClient(port, "oxsts");
    } else {
        client = createLspClient(oxstsIdeExecutable, "oxsts");
    }

    client.onNotification('workspace/navigateTo', (params: NavigateToParams) => {
        void navigateToLocation(params.locations);
    });

    await client.start();

    const oxstsTestController = new OxstsTestController(client);
    oxstsTestController.registerTestProvider(context);
    await oxstsTestController.refreshWorkspaceTests();
}

export async function stopOxstsClient() {
    await client.stop();
}

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

type VerificationCaseSpecification = {
    id: string
    label: string
    location: Location
}

type VerificationCaseRunResult = { 
    status: 'passed' | 'failed', 
    message?: string
};

export class OxstsTestController {

    protected readonly oxstsClient: LanguageClient;
    protected controller!: TestController;

    constructor(oxstsClient: LanguageClient) {
        this.oxstsClient = oxstsClient;
    }

    registerTestProvider(context: ExtensionContext) {
        this.controller = vscode.tests.createTestController('oxsts.verification', 'Semantifyr Verification Cases');
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
            true // isDefault
        );
        context.subscriptions.push(runProfile);

        const watcher = vscode.workspace.createFileSystemWatcher(`**/*.oxsts`)
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
        const uris = await vscode.workspace.findFiles("**/*.oxsts");
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
        const uris = await vscode.workspace.findFiles(new RelativePattern(folder, "**/*.oxsts"));
        this.forgetDocumentsUri(uris);
    }

    protected async discoverFolderTests(folder: WorkspaceFolder) {
        const uris = await vscode.workspace.findFiles(new RelativePattern(folder, "**/*.oxsts"));
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
        if (doc.languageId !== 'oxsts') {
            return;
        }

        await this.refreshDocumentUriTests(doc.uri);
    }

    protected async refreshDocumentUriTests(uri: Uri) {
        const documentTestItem = this.getDocumentTestItem(uri);

        const verificationCases = await client.sendRequest(ExecuteCommandRequest.type, {
            command: 'oxsts.case.discover',
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
                const result = await client.sendRequest(ExecuteCommandRequest.type, {
                    command: 'oxsts.case.verify',
                    arguments: [ { 
                        uri: testItem.uri!.toString(), 
                        range: {
                            start: {
                                line: testItem.range?.start.line,
                                character: testItem.range?.start.character,
                            },
                            end: {
                                line: testItem.range?.end.line,
                                character: testItem.range?.end.character,
                            },
                        }
                    } ]
                }, token) as VerificationCaseRunResult;

                if (result.status === 'passed') {
                    run.passed(testItem);
                } else {
                    run.failed(testItem, new TestMessage(result.message ?? 'Failed'));
                }
                // eslint-disable-next-line @typescript-eslint/no-unused-vars
            } catch (e: unknown) {
                run.errored(testItem, new TestMessage("Test run failed"));
            }
        }

        run.end();
    }
}
