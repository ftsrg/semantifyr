/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import * as vscode from 'vscode';

// FIXME: the common code excerpts should be extracted

export interface NavigateToParams {
  locations: Array<{
    uri: string;
    range: {
      start: { line: number; character: number };
      end: { line: number; character: number };
    };
  }>;
}

/**
 * Port of the VSCode extension's workspace/navigateTo handler. 
 * Mirrors subprojects/semantifyr-vscode/src/clients/oxsts-client.ts:25-61.
 */
export async function handleNavigateTo(params: NavigateToParams): Promise<void> {
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
