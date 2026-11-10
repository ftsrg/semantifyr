/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import { vi } from 'vitest';
import { CancellationTokenSource } from 'vscode-jsonrpc';

export { CancellationTokenSource };

type Listener<T> = (event: T) => unknown;

export class EventEmitter<T> {
    private listeners: Listener<T>[] = [];

    readonly event = (listener: Listener<T>) => {
        this.listeners.push(listener);
        return {
            dispose: () => {
                this.listeners = this.listeners.filter((it) => it !== listener);
            },
        };
    };

    fire(event: T) {
        for (const listener of this.listeners) {
            listener(event);
        }
    }

    dispose() {
        this.listeners = [];
    }
}

interface UriComponents {
    scheme: string;
    authority?: string;
    path?: string;
    query?: string;
    fragment?: string;
}

export class Uri {
    readonly scheme: string;
    readonly authority: string;
    readonly path: string;
    readonly query: string;
    readonly fragment: string;

    private constructor(components: UriComponents) {
        this.scheme = components.scheme;
        this.authority = components.authority ?? '';
        this.path = components.path ?? '';
        this.query = components.query ?? '';
        this.fragment = components.fragment ?? '';
    }

    static parse(value: string): Uri {
        const match = /^([^:/?#]+):(\/\/([^/?#]*))?([^?#]*)(\?([^#]*))?(#(.*))?$/.exec(value);
        if (!match) {
            throw new Error(`Not a URI: ${value}`);
        }
        return new Uri({ scheme: match[1]!, authority: match[3] ?? '', path: match[4] ?? '', query: match[6] ?? '', fragment: match[8] ?? '' });
    }

    static file(path: string): Uri {
        return new Uri({ scheme: 'file', path: path.replaceAll('\\', '/') });
    }

    static from(components: UriComponents): Uri {
        return new Uri(components);
    }

    get fsPath(): string {
        return this.path;
    }

    toString(): string {
        const authority = this.authority !== '' || this.scheme === 'file' ? `//${this.authority}` : '';
        const query = this.query === '' ? '' : `?${this.query}`;
        const fragment = this.fragment === '' ? '' : `#${this.fragment}`;
        return `${this.scheme}:${authority}${this.path}${query}${fragment}`;
    }
}

export class Position {
    readonly line: number;
    readonly character: number;

    constructor(line: number, character: number) {
        this.line = line;
        this.character = character;
    }
}

export class Range {
    readonly start: Position;
    readonly end: Position;

    constructor(start: Position, end: Position) {
        this.start = start;
        this.end = end;
    }
}

export class Location {
    readonly uri: Uri;
    readonly range: Range;

    constructor(uri: Uri, range: Range) {
        this.uri = uri;
        this.range = range;
    }
}

export class RelativePattern {
    readonly base: unknown;
    readonly pattern: string;

    constructor(base: unknown, pattern: string) {
        this.base = base;
        this.pattern = pattern;
    }
}

export class TestMessage {
    readonly message: string;
    location?: Location;
    contextValue?: string;

    constructor(message: string) {
        this.message = message;
    }
}

export const TestRunProfileKind = { Run: 1, Debug: 2, Coverage: 3 } as const;

export const ViewColumn = { Active: -1, Beside: -2, One: 1, Two: 2, Three: 3 } as const;

class TestItemCollection implements Iterable<[string, TestItem]> {
    private readonly items = new Map<string, TestItem>();

    get size(): number {
        return this.items.size;
    }

    add(item: TestItem) {
        this.items.set(item.id, item);
    }

    delete(id: string) {
        this.items.delete(id);
    }

    get(id: string): TestItem | undefined {
        return this.items.get(id);
    }

    forEach(callback: (item: TestItem, collection: TestItemCollection) => unknown) {
        for (const item of this.items.values()) {
            callback(item, this);
        }
    }

    [Symbol.iterator](): Iterator<[string, TestItem]> {
        return this.items.entries();
    }
}

export class TestItem {
    readonly id: string;
    readonly label: string;
    readonly uri: Uri | undefined;
    readonly children = new TestItemCollection();
    range: Range | undefined;
    canResolveChildren = false;

    constructor(id: string, label: string, uri: Uri | undefined) {
        this.id = id;
        this.label = label;
        this.uri = uri;
    }
}

type TestRunEvent =
    | { kind: 'enqueued' | 'started' | 'passed' | 'skipped'; item: TestItem }
    | { kind: 'failed' | 'errored'; item: TestItem; message: TestMessage };

class TestRun {
    readonly events: TestRunEvent[] = [];
    readonly output: { text: string; location: Location | undefined; item: TestItem | undefined }[] = [];
    ended = false;

    appendOutput(text: string, location?: Location, item?: TestItem) {
        this.output.push({ text, location, item });
    }

    enqueued(item: TestItem) {
        this.events.push({ kind: 'enqueued', item });
    }

    started(item: TestItem) {
        this.events.push({ kind: 'started', item });
    }

    passed(item: TestItem) {
        this.events.push({ kind: 'passed', item });
    }

    skipped(item: TestItem) {
        this.events.push({ kind: 'skipped', item });
    }

    failed(item: TestItem, message: TestMessage) {
        this.events.push({ kind: 'failed', item, message });
    }

    errored(item: TestItem, message: TestMessage) {
        this.events.push({ kind: 'errored', item, message });
    }

    end() {
        this.ended = true;
    }

    outcome(item: TestItem): TestRunEvent['kind'] | undefined {
        return this.events.filter((event) => event.item === item).at(-1)?.kind;
    }
}

interface TestRunProfile {
    label: string;
    kind: number;
    runHandler: (request: { include?: TestItem[]; exclude?: TestItem[] }, token: unknown) => Promise<void>;
    isDefault: boolean;
    dispose(): void;
}

export class TestController {
    readonly items = new TestItemCollection();
    readonly profiles: TestRunProfile[] = [];
    readonly runs: TestRun[] = [];
    readonly id: string;
    readonly label: string;
    resolveHandler: ((item: TestItem | undefined) => Promise<void>) | undefined;
    refreshHandler: (() => Promise<void>) | undefined;

    constructor(id: string, label: string) {
        this.id = id;
        this.label = label;
    }

    createTestItem(id: string, label: string, uri?: Uri): TestItem {
        return new TestItem(id, label, uri);
    }

    createRunProfile(label: string, kind: number, runHandler: TestRunProfile['runHandler'], isDefault = false): TestRunProfile {
        const profile = { label, kind, runHandler, isDefault, dispose: () => {} };
        this.profiles.push(profile);
        return profile;
    }

    createTestRun(): TestRun {
        const run = new TestRun();
        this.runs.push(run);
        return run;
    }

    dispose() {}
}

class FileSystemWatcher {
    readonly changed = new EventEmitter<Uri>();
    readonly created = new EventEmitter<Uri>();
    readonly deleted = new EventEmitter<Uri>();
    readonly onDidChange = this.changed.event;
    readonly onDidCreate = this.created.event;
    readonly onDidDelete = this.deleted.event;
    readonly glob: string;

    constructor(glob: string) {
        this.glob = glob;
    }

    dispose() {}
}

interface TextDocument {
    uri: Uri;
    languageId: string;
    getText(): string;
}

interface TextEditor {
    document: TextDocument;
    viewColumn: number;
}

interface TextDocumentContentProvider {
    provideTextDocumentContent(uri: Uri): string;
}

export const state = {
    configuration: new Map<string, unknown>(),
    files: [] as Uri[],
    commands: new Map<string, (...commandArguments: unknown[]) => unknown>(),
    controllers: [] as TestController[],
    watchers: [] as FileSystemWatcher[],
    contentProviders: new Map<string, TextDocumentContentProvider>(),
    configurationChanged: new EventEmitter<{ affectsConfiguration(section: string): boolean }>(),
    workspaceFoldersChanged: new EventEmitter<unknown>(),
    documentOpened: new EventEmitter<TextDocument>(),
    documentChanged: new EventEmitter<{ document: TextDocument }>(),
    filesRenamed: new EventEmitter<unknown>(),
};

function languageOf(uri: Uri): string {
    const extension = /\.([^./]+)$/.exec(uri.path)?.[1] ?? '';
    return { oxsts: 'oxsts', gamma: 'gamma', md: 'markdown' }[extension] ?? 'plaintext';
}

export function openDocument(uri: Uri): TextDocument {
    const provider = state.contentProviders.get(uri.scheme);
    return { uri, languageId: languageOf(uri), getText: () => provider?.provideTextDocumentContent(uri) ?? '' };
}

export const window = {
    activeTextEditor: undefined as TextEditor | undefined,
    createOutputChannel: vi.fn((name: string) => ({ name, append: vi.fn(), appendLine: vi.fn(), show: vi.fn(), dispose: vi.fn() })),
    showInformationMessage: vi.fn(async (_message: string) => undefined),
    showErrorMessage: vi.fn(async (_message: string) => undefined),
    showTextDocument: vi.fn(async (target: TextDocument | Uri, options?: { viewColumn?: number }) => {
        const document = target instanceof Uri ? openDocument(target) : target;
        const viewColumn = options?.viewColumn === ViewColumn.Beside ? ViewColumn.Two : options?.viewColumn ?? ViewColumn.One;
        const editor = { document, viewColumn };
        window.activeTextEditor = editor;
        return editor;
    }),
};

export const workspace = {
    getConfiguration: vi.fn((section: string) => ({
        get: <T>(key: string, defaultValue?: T): T | undefined => {
            const name = `${section}.${key}`;
            return state.configuration.has(name) ? state.configuration.get(name) as T : defaultValue;
        },
    })),
    onDidChangeConfiguration: state.configurationChanged.event,
    onDidChangeWorkspaceFolders: state.workspaceFoldersChanged.event,
    onDidOpenTextDocument: state.documentOpened.event,
    onDidChangeTextDocument: state.documentChanged.event,
    onDidRenameFiles: state.filesRenamed.event,
    createFileSystemWatcher: vi.fn((glob: string) => {
        const watcher = new FileSystemWatcher(glob);
        state.watchers.push(watcher);
        return watcher;
    }),
    findFiles: vi.fn(async (glob: string | RelativePattern) => {
        const pattern = typeof glob === 'string' ? glob : glob.pattern;
        const extension = pattern.slice(pattern.lastIndexOf('.'));
        return state.files.filter((uri) => uri.path.endsWith(extension));
    }),
    openTextDocument: vi.fn(async (uri: Uri) => openDocument(uri)),
    registerTextDocumentContentProvider: vi.fn((scheme: string, provider: TextDocumentContentProvider) => {
        state.contentProviders.set(scheme, provider);
        return { dispose: () => state.contentProviders.delete(scheme) };
    }),
};

export const commands = {
    registerCommand: vi.fn((id: string, handler: (...commandArguments: unknown[]) => unknown) => {
        state.commands.set(id, handler);
        return { dispose: () => state.commands.delete(id) };
    }),
    executeCommand: vi.fn(async (id: string, ...commandArguments: unknown[]) => state.commands.get(id)?.(...commandArguments)),
};

export const tests = {
    createTestController: vi.fn((id: string, label: string) => {
        const controller = new TestController(id, label);
        state.controllers.push(controller);
        return controller;
    }),
};

export function resetVscode() {
    vi.clearAllMocks();
    state.configuration.clear();
    state.files = [];
    state.commands.clear();
    state.controllers = [];
    state.watchers = [];
    state.contentProviders.clear();
    for (const emitter of [state.configurationChanged, state.workspaceFoldersChanged, state.documentOpened, state.documentChanged, state.filesRenamed]) {
        emitter.dispose();
    }
    window.activeTextEditor = undefined;
}

export function createExtensionContext(extensionPath: string) {
    return { extensionPath, subscriptions: [] as { dispose(): unknown }[] };
}

export default {
    CancellationTokenSource,
    EventEmitter,
    Location,
    Position,
    Range,
    RelativePattern,
    TestMessage,
    TestRunProfileKind,
    Uri,
    ViewColumn,
    commands,
    tests,
    window,
    workspace,
};
