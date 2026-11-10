/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import * as vscode from 'vscode';
import {
  FileSystemProviderCapabilities,
  FileSystemProviderError,
  FileSystemProviderErrorCode,
  RegisteredFileSystemProvider,
  RegisteredMemoryFile,
  registerCustomProvider,
  registerFileSystemOverlay,
} from '@codingame/monaco-vscode-files-service-override';
import type { OpenEditor } from '@codingame/monaco-vscode-editor-service-override';
import { configureDefaultWorkerFactory } from 'monaco-languageclient/workerFactory';
import { MonacoVscodeApiWrapper, type MonacoVscodeApiConfig, type ExtensionConfig } from 'monaco-languageclient/vscodeApiWrapper';
import { LanguageClientWrapper, LcWebSocket, LcWorker, type LanguageClientConfig } from 'monaco-languageclient/lcwrapper';
import { EditorApp, type EditorAppConfig } from 'monaco-languageclient/editorApp';
import { editor, Uri, type IDisposable } from 'monaco-editor';
import type { MessageTransports, Middleware } from 'vscode-languageclient';

import { normalizeBaseUrl, type NavigateToParams } from '@semantifyr/editor-common';
import type { ResolvedColorMode } from '../util/colorMode';
import { observeSocketClose, type SocketClose } from './reconnectController';
import type { ModelDocuments } from './modelDocuments';
import { DERIVED_SCHEME, fileUriOfLibrary, isLibraryFileUri, LIBRARY_SCHEME, libraryUriOf, TRACE_SCHEME } from './readOnlyDocuments';

// Synced from :semantifyr-vscode by the cloneVscodeLanguageAssets Gradle task.
import oxstsGrammar from '../../vscode/imported/syntaxes/oxsts.tmLanguage.json';
import gammaGrammar from '../../vscode/imported/syntaxes/gamma.tmLanguage.json';
import sysmlGrammar from '../../vscode/imported/syntaxes/sysml.tmLanguage.json';
import languageConfiguration from '../../vscode/imported/language-configuration.json';

const EDITOR_USER_CONFIG = {
  'editor.guides.bracketPairsHorizontal': 'active',
  'editor.wordBasedSuggestions': 'off',
  'editor.experimental.asyncTokenization': true,
  'editor.fontSize': 15,
  'editor.fontFamily': "'JetBrains Mono', monospace",
  'editor.fontLigatures': true,
  'editor.stickyScroll.enabled': true,
} as const;

const EDITOR_OPTIONS: editor.IStandaloneEditorConstructionOptions = {
  automaticLayout: true,
  minimap: { enabled: false },
  scrollBeyondLastLine: false,
  fontSize: 15,
  // an embed in a frame of fixed height scrolls on once its editor reaches an end
  scrollbar: { alwaysConsumeMouseWheel: false },
} as const;

const LANGUAGE_DEFS = [
  { id: 'oxsts', extensions: ['.oxsts'], aliases: ['OxSTS', 'oxsts'], scopeName: 'source.oxsts', grammarPath: './syntaxes/oxsts.tmLanguage.json' },
  { id: 'gamma', extensions: ['.gamma'], aliases: ['Gamma', 'gamma'], scopeName: 'source.gamma', grammarPath: './syntaxes/gamma.tmLanguage.json' },
  { id: 'sysml', extensions: ['.sysml'], aliases: ['SysML v2', 'sysml'], scopeName: 'source.sysml', grammarPath: './syntaxes/sysml.tmLanguage.json' },
];

const GRAMMAR_FILES: Record<string, unknown> = {
  'oxsts': oxstsGrammar,
  'gamma': gammaGrammar,
  'sysml': sysmlGrammar,
} as const;

function themeIdFor(colorMode: ResolvedColorMode): string {
  return colorMode === 'dark' ? 'Default Dark Modern' : 'Default Light Modern';
}

// Keep in sync with --accent / --accent-hover in styles.css.
const BRAND = {
  dark: { accent: '#ff5252', accentHover: '#e23e3e' },
  light: { accent: '#c00000', accentHover: '#a30000' },
} as const;

function accentColorCustomizations(palette: { accent: string; accentHover: string }): Record<string, string> {
  return {
    'focusBorder': palette.accent,
    'progressBar.background': palette.accent,
    'textLink.foreground': palette.accent,
    'textLink.activeForeground': palette.accentHover,
    'editorLink.activeForeground': palette.accent,
    'button.background': palette.accent,
    'button.hoverBackground': palette.accentHover,
    'list.highlightForeground': palette.accent,
    'editorSuggestWidget.highlightForeground': palette.accent,
    'editorSuggestWidget.focusHighlightForeground': palette.accent,
    'inputOption.activeBorder': palette.accent,
    'editorBracketMatch.border': palette.accent,
  };
}

function keywordTokenCustomizations(accent: string): { textMateRules: { scope: string[]; settings: { foreground: string } }[] } {
  return {
    textMateRules: [
      { scope: ['keyword.control'], settings: { foreground: accent } },
    ],
  };
}

const WORKBENCH_COLOR_CUSTOMIZATIONS = {
  '[Default Dark Modern]': accentColorCustomizations(BRAND.dark),
  '[Default Light Modern]': accentColorCustomizations(BRAND.light),
} as const;

const TOKEN_COLOR_CUSTOMIZATIONS = {
  '[Default Dark Modern]': keywordTokenCustomizations(BRAND.dark.accent),
  '[Default Light Modern]': keywordTokenCustomizations(BRAND.light.accent),
} as const;

function buildSemantifyrExtension(): ExtensionConfig {
  const files = new Map<string, string>([
    ['/language-configuration.json', JSON.stringify(languageConfiguration)],
    ...LANGUAGE_DEFS.map(({ id }) =>
      [`/syntaxes/${id}.tmLanguage.json`, JSON.stringify(GRAMMAR_FILES[id])] as [string, string],
    ),
  ]);
  return {
    config: {
      name: 'semantifyr',
      publisher: 'ftsrg',
      version: '0.0.1',
      engines: { vscode: '*' },
      contributes: {
        languages: LANGUAGE_DEFS.map(({ id, extensions, aliases }) => ({
          id, extensions, aliases, configuration: './language-configuration.json',
        })),
        grammars: LANGUAGE_DEFS.map(({ id, scopeName, grammarPath }) => ({
          language: id, scopeName, path: grammarPath,
        })),
      },
    },
    filesOrContents: files,
  };
}

const derivedTexts = new Map<string, string>();

// A document the page derives is opened through the editor's services, which then own its model
export async function openDerivedDocument(uri: string, text: string): Promise<vscode.TextDocument> {
  derivedTexts.set(uri, text);
  return vscode.workspace.openTextDocument(vscode.Uri.parse(uri));
}

type LibraryReader = (fileUri: string) => Promise<string | null>;

const NO_LIBRARY: LibraryReader = () => {
  return Promise.resolve(null);
};

let libraryReader = NO_LIBRARY;
let editorOpener: OpenEditor | null = null;

// Reads a library file a definition leads into, through the current language's session
export function setLibraryReader(reader: LibraryReader): () => void {
  libraryReader = reader;
  return () => {
    if (libraryReader === reader) {
      libraryReader = NO_LIBRARY;
    }
  };
}

// Opens a document the editor navigates to; without one, navigation stays in the current document
export function setEditorOpener(opener: OpenEditor): () => void {
  editorOpener = opener;
  return () => {
    if (editorOpener === opener) {
      editorOpener = null;
    }
  };
}

class LibraryFileSystemProvider extends RegisteredFileSystemProvider {
  constructor() {
    super(true);
    // without the stream capability the file service reads through readFile, which reads the file in first
    this.capabilities = FileSystemProviderCapabilities.FileReadWrite | FileSystemProviderCapabilities.PathCaseSensitive | FileSystemProviderCapabilities.Readonly;
  }

  private readonly reads = new Map<string, Promise<void>>();

  // the file service stats and reads at once, so both wait for one read
  private ensureRegistered(resource: vscode.Uri): Promise<void> {
    const key = resource.toString();
    let read = this.reads.get(key);
    if (!read) {
      read = this.register(resource);
      this.reads.set(key, read);
      read.catch(() => {
        this.reads.delete(key);
      });
    }
    return read;
  }

  private async register(resource: vscode.Uri): Promise<void> {
    const text = await libraryReader(fileUriOfLibrary(resource.toString()));
    if (text === null) {
      throw FileSystemProviderError.create('library file not found', FileSystemProviderErrorCode.FileNotFound);
    }
    this.registerFile(new RegisteredMemoryFile(resource, text));
  }

  override async stat(resource: vscode.Uri): ReturnType<RegisteredFileSystemProvider['stat']> {
    await this.ensureRegistered(resource);
    return super.stat(resource);
  }

  override async readFile(resource: vscode.Uri): Promise<Uint8Array> {
    await this.ensureRegistered(resource);
    return super.readFile(resource);
  }
}

function toLibraryUri(uri: vscode.Uri): vscode.Uri {
  const text = uri.toString();
  return isLibraryFileUri(text) ? vscode.Uri.parse(libraryUriOf(text)) : uri;
}

type DefinitionResult = vscode.Location | vscode.Location[] | vscode.LocationLink[] | null | undefined;

function definitionsIntoLibrary(result: DefinitionResult): DefinitionResult {
  if (!result) {
    return result;
  }
  if (!Array.isArray(result)) {
    return new vscode.Location(toLibraryUri(result.uri), result.range);
  }
  return result.map((entry: vscode.Location | vscode.LocationLink) => {
    if ('targetUri' in entry) {
      return { ...entry, targetUri: toLibraryUri(entry.targetUri) };
    }
    return new vscode.Location(toLibraryUri(entry.uri), entry.range);
  }) as vscode.Location[] | vscode.LocationLink[];
}

// a definition in a library opens the library's read-only document, which no client syncs
const LIBRARY_NAVIGATION: Middleware = {
  provideDefinition: async (document, position, token, next) => {
    return definitionsIntoLibrary(await next(document, position, token));
  },
  provideDeclaration: async (document, position, token, next) => {
    return definitionsIntoLibrary(await next(document, position, token));
  },
  provideTypeDefinition: async (document, position, token, next) => {
    return definitionsIntoLibrary(await next(document, position, token));
  },
};

const WORKSPACE_PATH = '/workspace/';
// the servers take a document only under a workspace file address, so a trace document goes under one no file of the session has
const TRACE_SERVER_PATH = '/workspace/.live-trace/';

const TRACE_ADDRESSES = {
  code2Protocol: (uri: vscode.Uri): string => {
    return uri.scheme === TRACE_SCHEME && uri.path.startsWith(WORKSPACE_PATH)
      ? vscode.Uri.file(`${TRACE_SERVER_PATH}${uri.path.slice(WORKSPACE_PATH.length)}`).toString()
      : uri.toString();
  },
  protocol2Code: (value: string): vscode.Uri => {
    const uri = vscode.Uri.parse(value);
    return uri.scheme === 'file' && uri.path.startsWith(TRACE_SERVER_PATH)
      ? uri.with({ scheme: TRACE_SCHEME, path: `${WORKSPACE_PATH}${uri.path.slice(TRACE_SERVER_PATH.length)}` })
      : uri;
  },
};

function documentSelectorOf(languageId: string): { language: string; scheme: string }[] {
  return [
    { language: languageId, scheme: 'file' },
    { language: languageId, scheme: TRACE_SCHEME },
  ];
}

let apiWrapperPromise: Promise<MonacoVscodeApiWrapper> | null = null;
let markMonacoReady: () => void = () => {};
// Monaco's editor API is touched only once the VS Code services are up, or it starts the standalone ones
const monacoReady = new Promise<void>((resolve) => {
  markMonacoReady = resolve;
});

export async function initializeMonaco(initialColorMode: ResolvedColorMode): Promise<MonacoVscodeApiWrapper> {
  if (apiWrapperPromise) return apiWrapperPromise;
  const vscodeApiConfig: MonacoVscodeApiConfig = {
    $type: 'extended',
    viewsConfig: {
      $type: 'EditorService',
      openEditorFunc: async (modelReference, options, sideBySide) => {
        return editorOpener ? editorOpener(modelReference, options, sideBySide) : undefined;
      },
    },
    userConfiguration: {
      json: JSON.stringify({
        'workbench.colorTheme': themeIdFor(initialColorMode),
        'workbench.colorCustomizations': WORKBENCH_COLOR_CUSTOMIZATIONS,
        'editor.tokenColorCustomizations': TOKEN_COLOR_CUSTOMIZATIONS,
        ...EDITOR_USER_CONFIG,
      }),
    },
    monacoWorkerFactory: configureDefaultWorkerFactory,
    extensions: [buildSemantifyrExtension()],
  };
  apiWrapperPromise = (async () => {
    // eslint-disable-next-line @typescript-eslint/no-unsafe-enum-assignment -- monaco-vscode-api declares the file enums in several modules
    registerCustomProvider(LIBRARY_SCHEME, new LibraryFileSystemProvider());
    const wrapper = new MonacoVscodeApiWrapper(vscodeApiConfig);
    await wrapper.start();
    const derivedContent: vscode.TextDocumentContentProvider = {
      provideTextDocumentContent: (uri) => {
        return derivedTexts.get(uri.toString()) ?? '';
      },
    };
    vscode.workspace.registerTextDocumentContentProvider(DERIVED_SCHEME, derivedContent);
    vscode.workspace.registerTextDocumentContentProvider(TRACE_SCHEME, derivedContent);
    markMonacoReady();
    return wrapper;
  })();
  return apiWrapperPromise;
}

export function whenMonacoReady(): Promise<void> {
  return monacoReady;
}

export function onDiagnosticsOf(uri: string, listener: () => void): () => void {
  let subscription: { dispose: () => void } | null = null;
  let disposed = false;
  void monacoReady.then(() => {
    if (disposed) {
      return;
    }
    subscription = vscode.languages.onDidChangeDiagnostics((event) => {
      if (event.uris.some((changed) => {
        return changed.toString() === uri;
      })) {
        listener();
      }
    });
  });
  return () => {
    disposed = true;
    subscription?.dispose();
  };
}

export interface EditorInstance {
  editorApp: EditorApp;
  fsOverlay: { dispose: () => void };
}

export async function createEditor(
  host: HTMLElement,
  colorMode: ResolvedColorMode,
  fileUri: vscode.Uri,
  language: string,
  initialCode: string,
): Promise<EditorInstance> {
  await initializeMonaco(colorMode);

  const fileSystemProvider = new RegisteredFileSystemProvider(false);
  fileSystemProvider.registerFile(new RegisteredMemoryFile(fileUri, initialCode));
  // eslint-disable-next-line @typescript-eslint/no-unsafe-enum-assignment -- monaco-vscode-api declares the file enums in several modules
  const fsOverlay = registerFileSystemOverlay(1, fileSystemProvider);

  const editorAppConfig: EditorAppConfig = {
    codeResources: {
      modified: { text: initialCode, uri: fileUri.path, enforceLanguageId: language },
    },
    editorOptions: EDITOR_OPTIONS,
  };
  const editorApp = new EditorApp(editorAppConfig);
  await editorApp.start(host);
  try {
    editorApp.getEditor()?.focus();
  } catch {
    /* focusing is best-effort */
  }

  return { editorApp, fsOverlay };
}

export function createModelDocuments(): ModelDocuments {
  const replacing = new Set<string>();
  let ready = false;

  const modelOf = (uri: string): editor.ITextModel | null => {
    return ready ? editor.getModel(Uri.parse(uri)) : null;
  };

  const afterInitialization = (action: () => void): void => {
    if (ready) {
      action();
      return;
    }
    void monacoReady.then(() => {
      ready = true;
      action();
    });
  };

  const onDidCreate = (uri: string, listener: (model: editor.ITextModel) => void): IDisposable => {
    return editor.onDidCreateModel((model) => {
      if (model.uri.toString() === uri) {
        listener(model);
      }
    });
  };

  return {
    isOpen: (uri) => {
      return modelOf(uri) !== null;
    },
    read: (uri) => {
      return modelOf(uri)?.getValue() ?? null;
    },
    replace: (uri, text) => {
      const model = modelOf(uri);
      if (!model || model.getValue() === text) {
        return;
      }
      replacing.add(uri);
      try {
        model.setValue(text);
      } finally {
        replacing.delete(uri);
      }
    },
    onDidOpen: (uri, listener) => {
      let disposed = false;
      let subscription: IDisposable | null = null;
      afterInitialization(() => {
        if (!disposed) {
          subscription = onDidCreate(uri, () => {
            listener();
          });
        }
      });
      return () => {
        disposed = true;
        subscription?.dispose();
      };
    },
    onDidChange: (uri, listener) => {
      let disposed = false;
      let created: IDisposable | null = null;
      let changed: IDisposable | null = null;
      const attach = (model: editor.ITextModel): void => {
        changed?.dispose();
        changed = model.onDidChangeContent(() => {
          listener({ replaced: replacing.has(uri) });
        });
      };
      afterInitialization(() => {
        if (disposed) {
          return;
        }
        const current = modelOf(uri);
        if (current) {
          attach(current);
        }
        created = onDidCreate(uri, attach);
      });
      return () => {
        disposed = true;
        created?.dispose();
        changed?.dispose();
      };
    },
  };
}

async function handleNavigateTo(params: NavigateToParams): Promise<void> {
  const activeEditor = vscode.window.activeTextEditor;
  if (!activeEditor) return;
  const currentUri = activeEditor.document.uri;
  const currentPosition = activeEditor.selection.start;
  const locations = params.locations.map(
    (loc) =>
      new vscode.Location(
        vscode.Uri.parse(loc.uri),
        new vscode.Range(
          loc.range.start.line,
          loc.range.start.character,
          loc.range.end.line,
          loc.range.end.character,
        ),
      ),
  );
  try {
    await vscode.commands.executeCommand(
      'editor.action.goToLocations',
      currentUri,
      currentPosition,
      locations,
      'peek',
    );
  } catch (e) {
    console.warn('semantifyr-live: editor.action.goToLocations failed', e);
  }
}

export interface LanguageClientCallbacks {
  onProgress: (params: unknown) => void;
  onStopped: () => void;
  onClose: (close: SocketClose) => void;
  onOpen?: () => void;
}

// LcWebSocket.start installs the onclose that stops the client, so the close code reaches the client through no handler of its own
class CloseObservingWebSocket extends LcWebSocket {
  private readonly onClose: (close: SocketClose) => void;
  private readonly onOpen: () => void;

  constructor(onClose: (close: SocketClose) => void, onOpen: () => void) {
    super();
    this.onClose = onClose;
    this.onOpen = onOpen;
  }

  override async init(...parameters: Parameters<LcWebSocket['init']>): Promise<MessageTransports> {
    const transports = await super.init(...parameters);
    const { webSocket } = this as unknown as { webSocket: WebSocket };
    webSocket.addEventListener('open', () => {
      this.onOpen();
    });
    return transports;
  }

  override start(): Promise<void> {
    const started = super.start();
    const { webSocket } = this as unknown as { webSocket: WebSocket | undefined };
    if (webSocket) {
      observeSocketClose(webSocket, this.onClose);
    }
    return started;
  }
}

function socketAddress(wsBaseUrl: string, flavorId: string, sessionKind: string | undefined): string {
  const address = `${wsBaseUrl}/ws/lsp/${encodeURIComponent(flavorId)}`;
  return sessionKind === undefined ? address : `${address}?kind=${encodeURIComponent(sessionKind)}`;
}

const LSP_STATE_STOPPED = 1;

export async function connectWorkerLanguageClient(
  languageId: string,
  workerUrl: URL,
  onStopped: () => void,
): Promise<LanguageClientWrapper> {
  const languageClient = new LanguageClientWrapper({
    languageId,
    connection: {
      options: {
        $family: 'Worker',
        realization: () => new LcWorker(),
        workerUrl,
        workerName: languageId,
        type: 'classic',
      },
    },
    clientOptions: {
      documentSelector: documentSelectorOf(languageId),
      middleware: LIBRARY_NAVIGATION,
      uriConverters: TRACE_ADDRESSES,
      workspaceFolder: {
        index: 0,
        name: 'workspace',
        uri: vscode.Uri.file('/workspace/'),
      },
    },
  });
  await languageClient.start();
  const rawClient = languageClient.getLanguageClient() as {
    onRequest?: (method: string, handler: () => null) => void;
    onDidChangeState?: (cb: (e: { newState: number; oldState: number }) => void) => void;
  } | undefined;
  // SysIDE's editor command registration: live has no such commands
  rawClient?.onRequest?.('sysml/registerTextEditorCommands', () => {
    return null;
  });
  rawClient?.onDidChangeState?.((stateChange) => {
    if (stateChange.newState === LSP_STATE_STOPPED) {
      onStopped();
    }
  });
  return languageClient;
}

export async function connectLanguageClient(
  backendUrl: string,
  flavorId: string,
  languageId: string,
  callbacks: LanguageClientCallbacks,
  sessionKind?: string,
): Promise<LanguageClientWrapper> {
  const { ws: wsBaseUrl } = normalizeBaseUrl(backendUrl);
  const languageClientConfig: LanguageClientConfig = {
    languageId,
    connection: {
      options: {
        $family: 'WebSocket',
        realization: () => new CloseObservingWebSocket(callbacks.onClose, callbacks.onOpen ?? (() => {})),
        webSocketUrl: socketAddress(wsBaseUrl, flavorId, sessionKind),
        disposeResources: true,
      },
    },
    clientOptions: {
      documentSelector: documentSelectorOf(languageId),
      middleware: LIBRARY_NAVIGATION,
      uriConverters: TRACE_ADDRESSES,
      workspaceFolder: {
        index: 0,
        name: 'workspace',
        uri: vscode.Uri.file('/workspace/'),
      },
    },
  };
  const languageClient = new LanguageClientWrapper(languageClientConfig);
  await languageClient.start();

  const rawClient = languageClient.getLanguageClient() as {
        onNotification: (method: string, cb: (params: unknown) => void) => void;
        onDidChangeState?: (cb: (e: { newState: number; oldState: number }) => void) => void;
      } | undefined;
  if (rawClient) {
    try {
      rawClient.onNotification('workspace/navigateTo', (params: unknown) => {
        void handleNavigateTo(params as NavigateToParams);
      });
    } catch (error) {
      console.warn('semantifyr-live: failed to register workspace/navigateTo', error);
    }
    try {
      rawClient.onNotification('$/progress', callbacks.onProgress);
    } catch (error) {
      console.warn('semantifyr-live: failed to register $/progress listener', error);
    }
    try {
      rawClient.onDidChangeState?.((stateChange) => {
        if (stateChange.newState === LSP_STATE_STOPPED) callbacks.onStopped();
      });
    } catch (error) {
      console.warn('semantifyr-live: failed to register onDidChangeState', error);
    }
  }

  return languageClient;
}
