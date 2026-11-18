/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

// @ts-check
import { spawn, spawnSync } from 'node:child_process';
import { copyFileSync, existsSync, mkdirSync, mkdtempSync, readdirSync, rmSync, writeFileSync } from 'node:fs';
import net from 'node:net';
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

const LAUNCHER_TIME_MS = 60 * 1000;

function firstJavaOnPath() {
    const found = spawnSync(process.platform === 'win32' ? 'where' : 'which', ['java'], { encoding: 'utf8' });
    const first = found.stdout.split(/\r?\n/)[0];
    return first === undefined || first === '' ? '(none)' : first;
}

async function describeLauncher() {
    console.error('---- the installed language server launcher, started by the harness ----');
    console.error(`JAVA_HOME: ${process.env.JAVA_HOME ?? '(not set)'}`);
    console.error(`java on the path: ${firstJavaOnPath()}`);
    if (process.platform !== 'win32') {
        const login = spawnSync(process.env.SHELL ?? '/bin/sh', ['-ilc', 'echo "JAVA_HOME=$JAVA_HOME"; command -v java; java -version 2>&1'], { encoding: 'utf8', timeout: 30_000 });
        console.error(`in a login shell: ${`${login.stdout}${login.stderr}`.trim()}`);
    }
    const installed =existsSync(extensionsDirectory) ? readdirSync(extensionsDirectory).find((name) => {
        return name.startsWith('ftsrg.semantifyr-');
    }) : undefined;
    const windows = process.platform === 'win32';
    const launcher = installed === undefined ? null : path.join(extensionsDirectory, installed, 'bin', 'oxsts.lang.ide', 'bin', `oxsts.lang.ide${windows ? '.bat' : ''}`);
    if (launcher === null || !existsSync(launcher)) {
        console.error(`no launcher at ${launcher ?? `an installed extension under ${extensionsDirectory}`}`);
        return;
    }
    const server = net.createServer();
    await new Promise((resolve) => {
        server.listen(0, '127.0.0.1', () => {
            resolve(undefined);
        });
    });
    const address = server.address();
    const port = address !== null && typeof address === 'object' ? address.port : 0;
    console.error(`launcher: ${launcher} --socket=${String(port)}`);
    const child = spawn(windows ? `"${launcher}"` : launcher, [`--socket=${String(port)}`], {
        shell: windows,
        stdio: ['ignore', 'pipe', 'pipe'],
    });
    let output = '';
    let errors = '';
    child.stdout.on('data', (chunk) => {
        output += String(chunk);
    });
    child.stderr.on('data', (chunk) => {
        errors += String(chunk);
    });
    /** @type {string} */
    const outcome = await new Promise((resolve) => {
        const timer = setTimeout(() => {
            resolve(`still running after ${String(LAUNCHER_TIME_MS / 1000)} s`);
        }, LAUNCHER_TIME_MS);
        server.on('connection', (socket) => {
            clearTimeout(timer);
            socket.destroy();
            resolve('connected to the harness');
        });
        child.on('error', (error) => {
            clearTimeout(timer);
            resolve(`could not start: ${error.message}`);
        });
        child.on('exit', (code, signal) => {
            clearTimeout(timer);
            resolve(`exited with code ${String(code)}${signal === null ? '' : ` on ${signal}`}`);
        });
    });
    if (windows && child.pid !== undefined) {
        spawnSync('taskkill', ['/pid', String(child.pid), '/T', '/F']);
    } else {
        child.kill();
    }
    server.close();
    console.error(`outcome: ${outcome}`);
    console.error(`---- standard output ----\n${output}`);
    console.error(`---- standard error ----\n${errors}`);
}

try {
    await runVSCodeCommand([
        '--install-extension', vsix,
        '--user-data-dir', userDataDirectory,
        '--extensions-dir', extensionsDirectory,
    ], {
        version,
        cachePath: testDirectory,
    });
    try {
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
                '--force-disable-user-env',
                '--user-data-dir', userDataDirectory,
                '--extensions-dir', extensionsDirectory,
            ],
        });
    } catch (error) {
        await describeLauncher();
        throw error;
    }
} finally {
    rmSync(workspace, { recursive: true, force: true });
}
