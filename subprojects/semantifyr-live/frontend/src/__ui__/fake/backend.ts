/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import type { CompiledOxstsResult } from '@semantifyr/editor-common'
import type { BrowserContext, Request, Route, WebSocketRoute } from 'playwright'

import { BUILTIN_LIBRARY, BUILTIN_LIBRARY_URI, FLAVORS, INFO, LAMP, LAMP_CASES, LIBRARIES, PORTFOLIOS, type StatedCase } from './defaults'
import { FakeSession, type Answers } from './session'

export type RestAnswer =
  | { status: number, body?: unknown }
  // the server does not answer at all
  | 'unreachable'

type RestHandler = (request: Request) => RestAnswer | Promise<RestAnswer>

type Connect =
  | 'accept'
  // takes the socket and never answers the start of the session
  | 'silent'
  | { code: number, reason: string }

interface RestCall {
  method: string
  path: string
}

function waitFor<T>(find: () => T | undefined, what: string, timeoutMs = 30_000): Promise<T> {
  const started = Date.now()
  return new Promise((resolve, reject) => {
    const poll = (): void => {
      const found = find()
      if (found !== undefined) {
        resolve(found)
      } else if (Date.now() - started > timeoutMs) {
        reject(new Error(`the fake backend waited ${timeoutMs / 1000} s for ${what}`))
      } else {
        setTimeout(poll, 20)
      }
    }
    poll()
  })
}

export class FakeBackend {
  readonly sessions: FakeSession[] = []
  readonly problems: string[] = []
  readonly restCalls: RestCall[] = []
  cases: readonly StatedCase[] = LAMP_CASES
  texts: Record<string, string> = { [BUILTIN_LIBRARY_URI]: BUILTIN_LIBRARY }
  compiled: CompiledOxstsResult = { status: 'compiled', oxsts: LAMP, message: null }
  answers: Partial<Answers> = {}
  connect: (session: FakeSession) => Connect = () => {
    return 'accept'
  }

  private readonly routes = new Map<string, RestHandler>([
    [
      'GET /api/info',
      () => {
        return { status: 200, body: INFO }
      },
    ],
    [
      'GET /api/flavors',
      () => {
        return { status: 200, body: FLAVORS }
      },
    ],
    [
      'GET /api/portfolios',
      () => {
        return { status: 200, body: PORTFOLIOS }
      },
    ],
    [
      'GET /api/libraries',
      () => {
        return { status: 200, body: LIBRARIES }
      },
    ],
  ])

  answer<Kind extends keyof Answers>(kind: Kind, handler: Answers[Kind]): void {
    this.answers = { ...this.answers, [kind]: handler }
  }

  // the answer to a REST path, as `GET /api/flavors`, or `DELETE /api/admin/sessions/*` for any last segment
  rest(route: string, handler: RestHandler): void {
    this.routes.set(route, handler)
  }

  session(index = -1): FakeSession {
    const session = this.sessions.at(index)
    if (!session) {
      throw new Error(`no session ${index} among ${this.sessions.length}`)
    }
    return session
  }

  openSession(matches: (session: FakeSession) => boolean = () => true): Promise<FakeSession> {
    return waitFor(() => {
      return this.sessions.findLast((session) => {
        return session.closed === null && matches(session)
      })
    }, 'an open session')
  }

  async install(context: BrowserContext, liveUrl: string): Promise<void> {
    await context.route(`${liveUrl}/api/**`, async (route) => {
      await this.answerRest(route)
    })
    await context.routeWebSocket(/\/ws\/lsp\//, (socket) => {
      this.openSocket(socket)
    })
  }

  private async answerRest(route: Route): Promise<void> {
    const path = new URL(route.request().url()).pathname
    const method = route.request().method()
    this.restCalls.push({ method, path })
    const handler = this.routes.get(`${method} ${path}`) ?? this.routes.get(`${method} ${path.replace(/\/[^/]+$/, '/*')}`)
    let answer: RestAnswer
    try {
      if (handler === undefined) {
        throw new Error(`the fake has no answer for ${method} ${path}`)
      }
      answer = await handler(route.request())
    } catch (error) {
      this.problems.push(error instanceof Error ? error.message : String(error))
      await route.fulfill({ status: 500 })
      return
    }
    if (answer === 'unreachable') {
      await route.abort('connectionrefused')
      return
    }
    await route.fulfill({
      status: answer.status,
      ...(answer.body === undefined ? {} : { contentType: 'application/json', body: JSON.stringify(answer.body) }),
    })
  }

  private openSocket(socket: WebSocketRoute): void {
    const url = socket.url()
    const flavorId = decodeURIComponent(new URL(url).pathname.split('/').at(-1) ?? '')
    const flavor = FLAVORS.flavors.find((candidate) => {
      return candidate.id === flavorId
    })
    if (flavor === undefined) {
      this.problems.push(`a session asked for the flavor ${flavorId}, which the fake does not serve`)
      void socket.close({ code: 4404, reason: `Unknown flavor ${flavorId}` })
      return
    }
    const session = new FakeSession(this, flavor, url)
    session.attach({
      send: (frame) => {
        socket.send(frame)
      },
      close: (code, reason) => {
        void socket.close({ code, reason })
      },
    })
    this.sessions.push(session)
    socket.onClose((code, reason) => {
      session.closed ??= { code: code ?? 1005, reason: reason ?? '' }
    })
    const connect = this.connect(session)
    if (typeof connect === 'object') {
      session.close(connect.code, connect.reason)
      return
    }
    socket.onMessage((frame) => {
      if (connect === 'silent') {
        return
      }
      try {
        session.receive(frame)
      } catch (error) {
        this.problems.push(error instanceof Error ? error.message : String(error))
      }
    })
  }
}
