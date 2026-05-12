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

import { normalizeBaseUrl } from '../api/urls';
import type { ResolvedColorMode } from '../util/colorMode';

// Grammars + language-configuration are synced from `:semantifyr-vscode` by the
// `cloneVscodeLanguageAssets` Gradle task; the extension is the single source of truth.
import oxstsGrammar from '../../vscode/imported/syntaxes/oxsts.tmLanguage.json';
import gammaGrammar from '../../vscode/imported/syntaxes/gamma.tmLanguage.json';
import languageConfiguration from '../../vscode/imported/language-configuration.json';

const EDITOR_USER_CONFIG = {
  'editor.guides.bracketPairsHorizontal': 'active',
  'editor.wordBasedSuggestions': 'off',
  'editor.experimental.asyncTokenization': true,
  'editor.fontSize': 15,
  'editor.fontFamily': "'JetBrains Mono', monospace",
  'editor.fontLigatures': true,
  // Sticky scroll: enabled. Earlier this had a visual bug in the codingame/monaco-vscode-editor-api
  // build where pinned headers shifted their gutter left of the rest of the editor. The cost of
  // navigating long OXSTS / Gamma files without pinned headers outweighs that residual misalignment,
  // so we ship sticky scroll on. If the gutter offset reappears, the next thing to try is bumping
  // monaco-vscode-editor-api or composing our own pinned-header strip.
  'editor.stickyScroll.enabled': true,
} as const;

const EDITOR_OPTIONS: editor.IStandaloneEditorConstructionOptions = {
  automaticLayout: true,
  minimap: { enabled: false },
  scrollBeyondLastLine: false,
  fontSize: 15,
} as const;

const LANGUAGE_DEFS = [
  { id: 'oxsts', extensions: ['.oxsts'], aliases: ['OxSTS', 'oxsts'], scopeName: 'source.oxsts', grammarPath: './syntaxes/oxsts.tmLanguage.json' },
  { id: 'gamma', extensions: ['.gamma'], aliases: ['Gamma', 'gamma'], scopeName: 'source.gamma', grammarPath: './syntaxes/gamma.tmLanguage.json' },
];

const GRAMMAR_FILES: Record<string, unknown> = {
  'oxsts': oxstsGrammar,
  'gamma': gammaGrammar,
} as const;

function themeIdFor(colorMode: ResolvedColorMode): string {
  return colorMode === 'dark' ? 'Default Dark Modern' : 'Default Light Modern';
}

// Semantifyr brand red. Keep in sync with --accent / --accent-hover in styles.css. Used to
// re-tint the bits of the default VSCode theme that ship in its accent blue (focus rings,
// progress bar, links, buttons) and to recolour control keywords - which Dark/Light Modern
// render in a mauve/pink - so the embedded editor reads as part of the app rather than as a
// stock VSCode surface.
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

function keywordTokenCustomizations(accent: string): { textMateRules: Array<{ scope: string[]; settings: { foreground: string } }> } {
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

let apiWrapperPromise: Promise<MonacoVscodeApiWrapper> | null = null;

async function ensureVscodeApi(initialColorMode: ResolvedColorMode): Promise<MonacoVscodeApiWrapper> {
  if (apiWrapperPromise) return apiWrapperPromise;
  const vscodeApiConfig: MonacoVscodeApiConfig = {
    $type: 'extended',
    viewsConfig: { $type: 'EditorService' },
    userConfiguration: {
      json: JSON.stringify({
        'workbench.colorTheme': themeIdFor(initialColorMode),
        // Theme-scoped, so a light/dark switch via applyColorTheme picks up the matching set
        // without any extra wiring.
        'workbench.colorCustomizations': WORKBENCH_COLOR_CUSTOMIZATIONS,
        'editor.tokenColorCustomizations': TOKEN_COLOR_CUSTOMIZATIONS,
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

/**
 * Update Monaco's active theme to match the page's resolved color mode. Awaits the API
 * wrapper's start so the call always lands AFTER the workbench config service exists; without
 * that, an early invocation (the colorMode effect runs before the editor's bootstrap effect)
 * would silently no-op via the catch and leave Monaco rendering whatever theme the wrapper
 * was initialised with - flashing the user when the two diverge. Returns silently on no-op
 * when no editor has been mounted at all (admin route, SSR snapshot, etc).
 */
export async function applyColorTheme(colorMode: ResolvedColorMode): Promise<void> {
  if (!apiWrapperPromise) {
    return
  }
  try {
    await apiWrapperPromise
    await vscode.workspace
      .getConfiguration()
      .update('workbench.colorTheme', themeIdFor(colorMode), vscode.ConfigurationTarget.Global)
  } catch (error) {
    console.warn('semantifyr-live: applyColorTheme failed', error)
  }
}

export function createFileUri(fileName: string): vscode.Uri {
  return vscode.Uri.file(`/workspace/${fileName}`);
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
  // Hand keyboard focus to the editor on mount so the user can start typing right away (also
  // re-runs on flavor / example switches, which remount via the React key).
  try {
    editorApp.getEditor()?.focus();
  } catch {
    /* focusing is best-effort */
  }

  return { editorApp, fsOverlay };
}

export interface SecondaryEditorHandle {
  dispose: () => void;
}

/**
 * Mount an additional Monaco editor in {@code host} that shares the workbench, models, and
 * FS overlays with the primary editor, so the active LanguageClient services hover,
 * diagnostics, and definitions for it without any extra wiring. Caller is responsible for
 * disposing when the host element is unmounted.
 *
 * <p>{@code initialContent} seeds the model and the registered overlay file; the witness Raw
 * view pulls this from the live-server via {@code semantifyr/live/session/document/read}
 * because the browser has no filesystem of its own.
 *
 * <p>Pre-condition: {@link ensureVscodeApi} has been called via the primary {@link createEditor};
 * we don't initialise the workbench here so two simultaneous starts can't race.
 */
export async function createSecondaryEditor(
  host: HTMLElement,
  fileUri: vscode.Uri,
  language: string,
  initialContent: string,
  options: { readOnly?: boolean } = {},
): Promise<SecondaryEditorHandle> {
  if (!apiWrapperPromise) {
    throw new Error('createSecondaryEditor called before the primary editor is up');
  }
  await apiWrapperPromise;

  const fileSystemProvider = new RegisteredFileSystemProvider(false);
  fileSystemProvider.registerFile(new RegisteredMemoryFile(fileUri, initialContent));
  const fsOverlay = registerFileSystemOverlay(1, fileSystemProvider);

  const editorAppConfig: EditorAppConfig = {
    codeResources: {
      modified: { text: initialContent, uri: fileUri.path, enforceLanguageId: language },
    },
    editorOptions: { ...EDITOR_OPTIONS, readOnly: options.readOnly ?? false },
  };
  const editorApp = new EditorApp(editorAppConfig);
  await editorApp.start(host);

  return {
    dispose: () => {
      try {
        void editorApp.dispose();
      } catch {
        /* ignore */
      }
      try {
        fsOverlay.dispose();
      } catch {
        /* ignore */
      }
    },
  };
}

/**
 * Wire shape of the {@code workspace/navigateTo} notification the OXSTS LSP server emits when
 * "go to definition" lands on a redefinition: the server hands the client several candidate
 * locations and asks it to surface a peek-style chooser. Mirrors the vscode extension's
 * {@code subprojects/semantifyr-vscode/src/clients/oxsts-client.ts} shape. Both SHOULD share
 * a common helper from {@code @semantifyr/editor-common} once the wire records consolidate.
 */
export interface NavigateToParams {
  locations: Array<{
    uri: string;
    range: {
      start: { line: number; character: number };
      end: { line: number; character: number };
    };
  }>;
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
}

export async function connectLanguageClient(
  backendUrl: string,
  flavorId: string,
  languageId: string,
  callbacks: LanguageClientCallbacks,
): Promise<LanguageClientWrapper> {
  const { ws: wsBaseUrl } = normalizeBaseUrl(backendUrl);
  const webSocketUrl = `${wsBaseUrl}/ws/lsp/${encodeURIComponent(flavorId)}`;
  const languageClientConfig: LanguageClientConfig = {
    languageId,
    connection: { options: { $type: 'WebSocketUrl', url: webSocketUrl } },
    clientOptions: {
      documentSelector: [languageId],
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
