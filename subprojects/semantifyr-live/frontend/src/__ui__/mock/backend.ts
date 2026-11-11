/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import type { BrowserContext, Request, Route, WebSocketRoute } from 'playwright'

import type { AdminStatusResponse } from '../../lib/api/types'
import { adminFixture, restFixture } from './fixtures'
import { MockSession, type Answers } from './session'

export type RestAnswer =
  | { status: number, body?: unknown }
  // the server does not answer at all
  | 'unreachable'

export type RestHandler = (request: Request, recorded: () => RestAnswer) => RestAnswer | Promise<RestAnswer>

export type Connect =
  | 'accept'
  // takes the socket and never answers the start of the session
  | 'silent'
  | { code: number, reason: string }

export interface RestCall {
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
        reject(new Error(`the mock backend waited ${timeoutMs / 1000} s for ${what}`))
      } else {
        setTimeout(poll, 20)
      }
    }
    poll()
  })
}

// the live backend inside the test: the REST calls and the session sockets of one browser context, answered from the recorded fixtures
// unless a test overrides an answer; whatever the frontend sends that the mock has no answer for is a problem that fails the test
export class MockBackend {
  readonly sessions: MockSession[] = []
  readonly problems: string[] = []
  readonly restCalls: RestCall[] = []
  answers: Partial<Answers> = {}
  connect: (session: MockSession) => Connect = () => {
    return 'accept'
  }

  delayMs = 150
  private status: AdminStatusResponse | null = null
  private readonly restHandlers = new Map<string, RestHandler>()

  get adminStatus(): AdminStatusResponse {
    this.status ??= structuredClone(adminFixture().status)
    return this.status
  }

  set adminStatus(status: AdminStatusResponse) {
    this.status = status
  }

  answer<Kind extends keyof Answers>(kind: Kind, handler: Answers[Kind]): void {
    this.answers = { ...this.answers, [kind]: handler }
  }

  // a handler for a REST path, as `GET /api/flavors`
  rest(route: string, handler: RestHandler): void {
    this.restHandlers.set(route, handler)
  }

  session(index = -1): MockSession {
    const session = this.sessions.at(index)
    if (!session) {
      throw new Error(`no session ${index} among ${this.sessions.length}`)
    }
    return session
  }

  // the next session opened from now on, or the first one matching
  nextSession(matches: (session: MockSession) => boolean = () => true): Promise<MockSession> {
    const known = this.sessions.length
    return waitFor(() => {
      return this.sessions.slice(known).find(matches)
    }, 'a session')
  }

  openSession(matches: (session: MockSession) => boolean = () => true): Promise<MockSession> {
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

  private recordedRest(method: string, path: string): RestAnswer {
    const rest = restFixture()
    const ok = (body: unknown): RestAnswer => {
      return { status: 200, body }
    }
    if (method === 'GET') {
      switch (path) {
        case '/api/info':
          return ok(rest.info)
        case '/api/flavors':
          return ok(rest.flavors)
        case '/api/portfolios':
          return ok(rest.portfolios)
        case '/api/libraries':
          return ok(rest.libraries)
        case '/api/admin/whoami':
          return ok(adminFixture().whoami)
        case '/api/admin/config':
          return ok(adminFixture().config)
        case '/api/admin/status':
          return ok(this.adminStatus)
      }
    }
    if (method === 'DELETE') {
      const session = /^\/api\/admin\/sessions\/(.+)$/.exec(path)?.[1]
      if (session !== undefined) {
        this.adminStatus = { ...this.adminStatus, sessions: this.adminStatus.sessions.filter((candidate) => {
          return candidate.sessionId !== decodeURIComponent(session)
        }) }
        return { status: 200 }
      }
      if (/^\/api\/admin\/verifications\/.+$/.test(path)) {
        return { status: 200 }
      }
    }
    throw new Error(`the mock has no answer for ${method} ${path}`)
  }

  private async answerRest(route: Route): Promise<void> {
    const request = route.request()
    const path = new URL(request.url()).pathname
    const method = request.method()
    this.restCalls.push({ method, path })
    const recorded = (): RestAnswer => {
      return this.recordedRest(method, path)
    }
    const handler = this.restHandlers.get(`${method} ${path}`) ?? this.restHandlers.get(`${method} ${path.replace(/\/[^/]+$/, '/*')}`)
    let answer: RestAnswer
    try {
      answer = handler ? await handler(request, recorded) : recorded()
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
    const session = new MockSession({
      flavorId,
      url,
      answers: () => {
        return this.answers
      },
      delayMs: () => {
        return this.delayMs
      },
      problem: (problem) => {
        this.problems.push(problem)
      },
    })
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
