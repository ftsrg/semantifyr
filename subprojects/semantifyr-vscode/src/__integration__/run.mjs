/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

// @ts-check
import { copyFileSync, mkdtempSync, rmSync } from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import * as esbuild from 'esbuild';
import { runTests } from '@vscode/test-electron';
import packageJson from '../../package.json' with { type: 'json' };

const extensionDevelopmentPath = fileURLToPath(new URL('../..', import.meta.url));
const testDirectory = path.join(extensionDevelopmentPath, '.vscode-test');
const suite = path.join(testDirectory, 'suite.cjs');

await esbuild.build({
    entryPoints: [fileURLToPath(new URL('suite.ts', import.meta.url))],
    outfile: suite,
    bundle: true,
    external: ['vscode'],
    format: 'cjs',
    platform: 'node',
    target: 'ES2023',
});

const workspace = mkdtempSync(path.join(os.tmpdir(), 'semantifyr-vscode-'));
copyFileSync(fileURLToPath(new URL('../../../../oxsts-test-models/smoke/smoke.oxsts', import.meta.url)), path.join(workspace, 'smoke.oxsts'));

// keeps the window off a desktop session, and a VS Code terminal would run Electron as plain Node
delete process.env.DISPLAY;
delete process.env.WAYLAND_DISPLAY;
delete process.env.ELECTRON_RUN_AS_NODE;

try {
    await runTests({
        version: packageJson.engines.vscode.replace('^', ''),
        cachePath: testDirectory,
        extensionDevelopmentPath,
        extensionTestsPath: suite,
        launchArgs: [
            workspace,
            '--disable-extensions',
            '--disable-workspace-trust',
            '--disable-gpu',
            '--no-sandbox',
            '--ozone-platform=headless',
            '--user-data-dir', path.join(testDirectory, 'user-data'),
            '--extensions-dir', path.join(testDirectory, 'extensions'),
        ],
    });
} finally {
    rmSync(workspace, { recursive: true, force: true });
}
