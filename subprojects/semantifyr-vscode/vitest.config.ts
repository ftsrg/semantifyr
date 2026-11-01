/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import { fileURLToPath } from 'node:url';
import { defineConfig } from 'vitest/config';

function stub(name: string): string {
    return fileURLToPath(new URL(`src/__tests__/stubs/${name}.ts`, import.meta.url));
}

export default defineConfig({
    resolve: {
        alias: [
            { find: /^vscode$/, replacement: stub('vscode') },
            { find: /^vscode-languageclient$/, replacement: stub('vscode-languageclient') },
            { find: /^vscode-languageclient\/node$/, replacement: stub('vscode-languageclient-node') },
        ],
    },
    test: {
        include: ['src/__tests__/**/*.test.ts'],
        environment: 'node',
    },
});
