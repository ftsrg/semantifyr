/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

// @ts-check
import { copyFileSync, mkdirSync, mkdtempSync, rmSync, writeFileSync } from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import * as esbuild from 'esbuild';
import { runTests, runVSCodeCommand } from '@vscode/test-electron';
import packageJson from '../../package.json' with { type: 'json' };

const vsix = process.env.SEMANTIFYR_VSIX;
if (!vsix) {
    throw new Error('SEMANTIFYR_VSIX names no packed extension');
}

const testDirectory = fileURLToPath(new URL('../../.vscode-test', import.meta.url));
const suite = path.join(testDirectory, 'suite.cjs');
const extensionsDirectory = path.join(testDirectory, 'extensions');
const userDataDirectory = path.join(testDirectory, 'user-data');
const version = packageJson.engines.vscode.replace('^', '');

await esbuild.build({
    entryPoints: [fileURLToPath(new URL('suite.ts', import.meta.url))],
    outfile: suite,
    bundle: true,
    external: ['vscode'],
    format: 'cjs',
    platform: 'node',
    target: 'ES2023',
});

// VS Code runs a test suite only beside an extension under development, so the suite gets one that contributes nothing
const harness = path.join(testDirectory, 'harness');
mkdirSync(harness, { recursive: true });
writeFileSync(path.join(harness, 'package.json'), JSON.stringify({
    name: 'semantifyr-end-to-end',
    publisher: 'ftsrg',
    version: '0.0.0',
    engines: packageJson.engines,
}));

const workspace = mkdtempSync(path.join(os.tmpdir(), 'semantifyr-vscode-'));
copyFileSync(fileURLToPath(new URL('../../../../oxsts-test-models/smoke/smoke.semantifyr', import.meta.url)), path.join(workspace, 'smoke.semantifyr'));

// keeps the window off a desktop session, and a VS Code terminal would run Electron as plain Node
delete process.env.DISPLAY;
delete process.env.WAYLAND_DISPLAY;
delete process.env.ELECTRON_RUN_AS_NODE;

rmSync(extensionsDirectory, { recursive: true, force: true });

try {
    await runVSCodeCommand([
        '--install-extension', vsix,
        '--user-data-dir', userDataDirectory,
        '--extensions-dir', extensionsDirectory,
    ], {
        version,
        cachePath: testDirectory,
    });
    await runTests({
        version,
        cachePath: testDirectory,
        extensionDevelopmentPath: harness,
        extensionTestsPath: suite,
        launchArgs: [
            workspace,
            '--disable-workspace-trust',
            '--disable-gpu',
            '--no-sandbox',
            '--ozone-platform=headless',
            '--user-data-dir', userDataDirectory,
            '--extensions-dir', extensionsDirectory,
        ],
    });
} finally {
    rmSync(workspace, { recursive: true, force: true });
}
