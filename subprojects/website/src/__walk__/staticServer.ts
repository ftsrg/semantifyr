/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import { existsSync, readFileSync, statSync } from 'node:fs';
import { createServer, type Server } from 'node:http';
import type { AddressInfo } from 'node:net';
import { extname, join, normalize, resolve } from 'node:path';
import type { TestProject } from 'vitest/node';

const SITE = resolve(import.meta.dirname, '../../build/docusaurus');
export const LIVE = resolve(import.meta.dirname, '../../../semantifyr-live/frontend/dist');

export const CONTENT_TYPES: Readonly<Record<string, string>> = {
  '.html': 'text/html; charset=utf-8',
  '.js': 'text/javascript; charset=utf-8',
  '.mjs': 'text/javascript; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.json': 'application/json',
  '.webmanifest': 'application/manifest+json',
  '.wasm': 'application/wasm',
  '.svg': 'image/svg+xml',
  '.png': 'image/png',
  '.ico': 'image/x-icon',
  '.woff': 'font/woff',
  '.woff2': 'font/woff2',
  '.ttf': 'font/ttf',
  '.xml': 'application/xml',
  '.txt': 'text/plain; charset=utf-8',
};

declare module 'vitest' {
  export interface ProvidedContext {
    siteUrl: string;
  }
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

// the site's own address is where its build says it is hosted, so the pages' links hold as they are
function basePathOf(): string {
  const canonical = /<link[^>]+rel="?canonical"?[^>]+href="?([^"\s>]+)/.exec(readFileSync(join(SITE, 'index.html'), 'utf8'))?.[1];
  if (canonical === undefined) {
    throw new Error('The first page of the built site names no canonical address');
  }
  return new URL(canonical).pathname;
}

// the built site as its host serves it, at its base path, with its page of a missing address
function serve(basePath: string): Server {
  return createServer((request, response) => {
    const path = new URL(request.url ?? '/', 'http://localhost').pathname;
    const file = path.startsWith(basePath) ? fileOf(SITE, path.slice(basePath.length)) : null;
    if (file === null) {
      response.writeHead(404, { 'Content-Type': CONTENT_TYPES['.html'] });
      response.end(readFileSync(join(SITE, '404.html')));
      return;
    }
    response.writeHead(200, { 'Content-Type': CONTENT_TYPES[extname(file)] ?? 'application/octet-stream' });
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
  return async () => {
    await new Promise<void>((stopped) => {
      server.close(() => {
        stopped();
      });
    });
  };
}
