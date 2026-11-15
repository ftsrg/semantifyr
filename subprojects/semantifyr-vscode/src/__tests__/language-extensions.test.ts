/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import { readFileSync } from 'node:fs';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { activate } from '../extension.js';
import { createExtensionContext, resetVscode, state, Uri } from './stubs/vscode.js';
import { LanguageClient } from './stubs/vscode-languageclient-node.js';

const grammarWorkflow = readFileSync(new URL('../../../oxsts.lang/src/main/java/hu/bme/mit/semantifyr/oxsts/lang/GenerateOxsts.mwe2', import.meta.url), 'utf8');
const languageExtensions = /fileExtensions = "([^"]+)"/.exec(grammarWorkflow)![1]!.split(',');
const manifest = JSON.parse(readFileSync(new URL('../../package.json', import.meta.url), 'utf8')) as {
    activationEvents: string[];
    contributes: { languages: { id: string; extensions: string[] }[] };
};

beforeEach(() => {
    resetVscode();
    LanguageClient.respond = () => [];
});

afterEach(() => {
    LanguageClient.disposeAll();
});

describe('the language extensions of the grammar workflow', () => {
    it('are the extensions the manifest associates with the OXSTS language', () => {
        const language = manifest.contributes.languages.find((it) => it.id === 'oxsts')!;

        expect(language.extensions).toEqual(languageExtensions.map((extension) => `.${extension}`));
    });

    it('each activate the extension on a workspace holding a file', () => {
        const activations = manifest.activationEvents.filter((it) => it.startsWith('workspaceContains:**/*.') && !it.endsWith('.gamma') && !it.endsWith('.gcd'));

        expect(activations).toEqual(languageExtensions.map((extension) => `workspaceContains:**/*.${extension}`));
    });

    it('are the files the OXSTS verification cases are discovered and watched in', async () => {
        state.files = languageExtensions.map((extension) => Uri.parse(`file:///workspace/lamp.${extension}`));
        const discovered: string[] = [];
        LanguageClient.respond = (_instance, _method, parameters) => {
            discovered.push((parameters as { arguments: string[] }).arguments[0]!);
            return [];
        };

        await activate(createExtensionContext('/extension') as never);

        expect(state.watchers.map((watcher) => watcher.glob)).toContain(`**/*.{${languageExtensions.join(',')}}`);
        expect(discovered).toEqual(state.files.map((uri) => uri.toString()));
    });
});
