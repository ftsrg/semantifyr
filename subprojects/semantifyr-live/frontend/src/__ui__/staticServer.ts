/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import { existsSync, readFileSync, statSync } from 'node:fs'
import { createServer, type Server } from 'node:http'
import type { AddressInfo } from 'node:net'
import { extname, join, normalize, resolve } from 'node:path'
import type { TestProject } from 'vitest/node'

const FRONTEND = resolve(import.meta.dirname, '../..')
const DIST = join(FRONTEND, 'dist')
const SYSML_WORKER = join(FRONTEND, 'build/sysml-worker')

export const CONTENT_TYPES: Readonly<Record<string, string>> = {
  '.html': 'text/html; charset=utf-8',
  '.js': 'text/javascript; charset=utf-8',
  '.cjs': 'text/javascript; charset=utf-8',
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
  '.sysml': 'text/plain; charset=utf-8',
  '.kerml': 'text/plain; charset=utf-8',
}

declare module 'vitest' {
  export interface ProvidedContext {
    liveUrl: string
  }
}

function fileOf(root: string, path: string): string | null {
  const file = normalize(join(root, decodeURIComponent(path)))
  if (!file.startsWith(root) || !existsSync(file) || !statSync(file).isFile()) {
    return null
  }
  return file
}

// the production build as the backend serves it, the SysML worker's files beside it; /api and /ws never reach it, the mock backend answers them
function serve(): Server {
  return createServer((request, response) => {
    const path = new URL(request.url ?? '/', 'http://localhost').pathname
    const file = path.startsWith('/sysml-worker/')
      ? fileOf(SYSML_WORKER, path.slice('/sysml-worker/'.length))
      : fileOf(DIST, path) ?? (extname(path) === '' ? join(DIST, 'index.html') : null)
    if (file === null) {
      response.writeHead(404)
      response.end()
      return
    }
    response.writeHead(200, { 'Content-Type': CONTENT_TYPES[extname(file)] ?? 'application/octet-stream' })
    response.end(readFileSync(file))
  })
}

export default async function setup(project: TestProject): Promise<() => Promise<void>> {
  if (!existsSync(join(DIST, 'index.html'))) {
    throw new Error('The UI tests serve the production build: run npm run assemble first')
  }
  if (!existsSync(join(SYSML_WORKER, 'syside-worker.cjs'))) {
    throw new Error('The UI tests serve the SysML worker: run ./gradlew :semantifyr-live-frontend:cloneSysmlWorker first')
  }
  const server = serve()
  await new Promise<void>((started) => {
    server.listen(Number(process.env.SEMANTIFYR_UI_PORT ?? 0), '127.0.0.1', started)
  })
  project.provide('liveUrl', `http://127.0.0.1:${(server.address() as AddressInfo).port}`)
  return async () => {
    await new Promise<void>((stopped) => {
      server.close(() => {
        stopped()
      })
    })
  }
}
