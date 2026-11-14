/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import { existsSync, readdirSync, readFileSync, statSync } from 'node:fs';
import { createServer, type Server } from 'node:http';
import type { AddressInfo } from 'node:net';
import { extname, join, normalize, resolve } from 'node:path';
import type { TestProject } from 'vitest/node';
import { CONTENT_TYPES } from '../../../semantifyr-live/frontend/src/__ui__/staticServer';

const SITE = resolve(import.meta.dirname, '../../build/docusaurus');
export const LIVE = resolve(import.meta.dirname, '../../../semantifyr-live/frontend/dist');

declare module 'vitest' {
  export interface ProvidedContext {
    siteUrl: string;
    liveUrl: string;
  }
}

export function contentTypeOf(file: string): string {
  return CONTENT_TYPES[extname(file)] ?? 'application/octet-stream';
}

export function fileOf(root: string, path: string): string | null {
  const file = normalize(join(root, decodeURIComponent(path)));
  if (!file.startsWith(root) || !existsSync(file)) {
    return null;
  }
  if (statSync(file).isDirectory()) {
    return existsSync(join(file, 'index.html')) ? join(file, 'index.html') : null;
  }
  return file;
}

// the pages' links are written for the base path the build is hosted at, so the site is served there
function basePathOf(): string {
  const canonical = /<link[^>]+rel="?canonical"?[^>]+href="?([^"\s>]+)/.exec(readFileSync(join(SITE, 'index.html'), 'utf8'))?.[1];
  if (canonical === undefined) {
    throw new Error('The first page of the built site names no canonical address');
  }
  return new URL(canonical).pathname;
}

// the walk answers live at the address the site's build was given for it, so the site needs no build of its own for the walk
function liveUrlOf(): string {
  const scripts = join(SITE, 'assets/js');
  for (const file of readdirSync(scripts)) {
    const named = /liveBackendUrl:"([^"]+)"/.exec(readFileSync(join(scripts, file), 'utf8'))?.[1];
    if (named !== undefined) {
      return new URL(named).origin;
    }
  }
  throw new Error('The built site names no address of live in its configuration');
}

function serve(basePath: string): Server {
  return createServer((request, response) => {
    const path = new URL(request.url ?? '/', 'http://localhost').pathname;
    const file = path.startsWith(basePath) ? fileOf(SITE, path.slice(basePath.length)) : null;
    if (file === null) {
      response.writeHead(404, { 'Content-Type': contentTypeOf('404.html') });
      response.end(readFileSync(join(SITE, '404.html')));
      return;
    }
    response.writeHead(200, { 'Content-Type': contentTypeOf(file) });
    response.end(readFileSync(file));
  });
}

export default async function setup(project: TestProject): Promise<() => Promise<void>> {
  if (!existsSync(join(SITE, 'index.html'))) {
    throw new Error('The keyboard walk serves the built site: run ./gradlew :website:npmAssemble first');
  }
  if (!existsSync(join(LIVE, 'index.html'))) {
    throw new Error('The keyboard walk opens the embedded examples in live\'s production build: run ./gradlew :semantifyr-live-frontend:npmAssemble first');
  }
  const basePath = basePathOf();
  const server = serve(basePath);
  await new Promise<void>((started) => {
    server.listen(0, '127.0.0.1', started);
  });
  project.provide('siteUrl', `http://127.0.0.1:${(server.address() as AddressInfo).port}${basePath}`);
  project.provide('liveUrl', liveUrlOf());
  return async () => {
    await new Promise<void>((stopped) => {
      server.close(() => {
        stopped();
      });
    });
  };
}
