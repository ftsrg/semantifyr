/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import * as vscode from 'vscode';
import {
  RegisteredFileSystemProvider,
  RegisteredMemoryFile,
  registerFileSystemOverlay,
} from '@codingame/monaco-vscode-files-service-override';
import { configureDefaultWorkerFactory } from 'monaco-languageclient/workerFactory';
import { MonacoVscodeApiWrapper, type MonacoVscodeApiConfig, type ExtensionConfig } from 'monaco-languageclient/vscodeApiWrapper';
import { LanguageClientWrapper, type LanguageClientConfig } from 'monaco-languageclient/lcwrapper';
import { EditorApp, type EditorAppConfig } from 'monaco-languageclient/editorApp';
import type { editor } from 'monaco-editor';

import { normalizeBaseUrl } from './urls';
import { handleNavigateTo, type NavigateToParams } from './handlers';
import type { ResolvedColorMode } from './theme';

import oxstsGrammar from '../vscode/syntaxes/oxsts.tmLanguage.json';
import xstsGrammar from '../vscode/syntaxes/xsts.tmLanguage.json';
import gammaGrammar from '../vscode/syntaxes/gamma.tmLanguage.json';
import languageConfiguration from '../vscode/language-configuration.json';

const EDITOR_USER_CONFIG = {
  'editor.guides.bracketPairsHorizontal': 'active',
  'editor.wordBasedSuggestions': 'off',
  'editor.experimental.asyncTokenization': true,
  'editor.fontSize': 15,
  'editor.fontFamily': "'JetBrains Mono', monospace",
  'editor.fontLigatures': true,
} as const;

const EDITOR_OPTIONS: editor.IStandaloneEditorConstructionOptions = {
  automaticLayout: true,
  minimap: { enabled: false },
  scrollBeyondLastLine: false,
  fontSize: 15,
} as const;

const LANGUAGE_DEFS = [
  { id: 'oxsts', extensions: ['.oxsts'], aliases: ['OxSTS', 'oxsts'], scopeName: 'source.oxsts', grammarPath: './syntaxes/oxsts.tmLanguage.json' },
  { id: 'xsts', extensions: ['.xsts'], aliases: ['XSTS', 'xsts'], scopeName: 'source.xsts', grammarPath: './syntaxes/xsts.tmLanguage.json' },
  { id: 'gamma', extensions: ['.gamma'], aliases: ['Gamma', 'gamma'], scopeName: 'source.gamma', grammarPath: './syntaxes/gamma.tmLanguage.json' },
];

const GRAMMAR_FILES: Record<string, unknown> = {
  'oxsts': oxstsGrammar,
  'xsts': xstsGrammar,
  'gamma': gammaGrammar,
} as const;

function themeIdFor(colorMode: ResolvedColorMode): string {
  return colorMode === 'dark' ? 'Default Dark Modern' : 'Default Light Modern';
}

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

let apiWrapperPromise: Promise<MonacoVscodeApiWrapper> | null = null;

async function ensureVscodeApi(initialColorMode: ResolvedColorMode): Promise<MonacoVscodeApiWrapper> {
  if (apiWrapperPromise) return apiWrapperPromise;
  const vscodeApiConfig: MonacoVscodeApiConfig = {
    $type: 'extended',
    viewsConfig: { $type: 'EditorService' },
    userConfiguration: {
      json: JSON.stringify({
        'workbench.colorTheme': themeIdFor(initialColorMode),
        ...EDITOR_USER_CONFIG,
      }),
    },
    monacoWorkerFactory: configureDefaultWorkerFactory,
    extensions: [buildSemantifyrExtension()],
  };
  apiWrapperPromise = (async () => {
    const wrapper = new MonacoVscodeApiWrapper(vscodeApiConfig);
    await wrapper.start();
    return wrapper;
  })();
  return apiWrapperPromise;
}

export async function applyColorTheme(colorMode: ResolvedColorMode): Promise<void> {
  try {
    await vscode.workspace
      .getConfiguration()
      .update('workbench.colorTheme', themeIdFor(colorMode), vscode.ConfigurationTarget.Global);
  } catch {
    /* config service not ready yet */
  }
}

export function createFileUri(language: string): vscode.Uri {
  return vscode.Uri.file(`/workspace/snippet.${language}`);
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
  await ensureVscodeApi(colorMode);

  const fileSystemProvider = new RegisteredFileSystemProvider(false);
  fileSystemProvider.registerFile(new RegisteredMemoryFile(fileUri, initialCode));
  const fsOverlay = registerFileSystemOverlay(1, fileSystemProvider);

  const editorAppConfig: EditorAppConfig = {
    codeResources: {
      modified: { text: initialCode, uri: fileUri.path, enforceLanguageId: language },
    },
    editorOptions: EDITOR_OPTIONS,
  };
  const editorApp = new EditorApp(editorAppConfig);
  await editorApp.start(host);

  return { editorApp, fsOverlay };
}

export interface LanguageClientCallbacks {
  onProgress: (params: unknown) => void;
  onStopped: () => void;
}

export async function connectLanguageClient(
  backendUrl: string,
  language: string,
  callbacks: LanguageClientCallbacks,
): Promise<LanguageClientWrapper> {
  const { ws: wsBaseUrl } = normalizeBaseUrl(backendUrl);
  const webSocketUrl = `${wsBaseUrl}/ws/lsp/${encodeURIComponent(language)}`;
  const languageClientConfig: LanguageClientConfig = {
    languageId: language,
    connection: { options: { $type: 'WebSocketUrl', url: webSocketUrl } },
    clientOptions: {
      documentSelector: [language],
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
      // State.Stopped from vscode-languageclient -- hardcoded to avoid pulling
      // the node entry point into the browser bundle.
      rawClient.onDidChangeState?.((stateChange) => {
        if (stateChange.newState === 1) callbacks.onStopped();
      });
    } catch (error) {
      console.warn('semantifyr-live: failed to register onDidChangeState', error);
    }
  }

  return languageClient;
}
