/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import { readFileSync, writeFileSync } from 'node:fs'
import { join } from 'node:path'
import { parseArgs } from 'node:util'
import { chromium, type BrowserContext, type Page } from 'playwright'
import type {
  CompiledOxstsResult,
  Range,
  VerificationCaseResult,
  VerificationCaseSpecification,
  WitnessValidationResult,
} from '@semantifyr/editor-common'

import { FIXTURES_DIRECTORY, textKey, type AdminFixture, type Diagnostic, type FlavorFixture, type ProgressValue, type RestFixture } from './fixtures.ts'
import { isNotification, isRequest, parseMessage, type Message, type MessageId, type RequestMessage } from './jsonRpc.ts'

const usage = `Usage: npm run ui:record -- [options]

Records what a live server answers while a browser walks every example of every language, verifies the first example of each and opens its documents, cancels a run, and reads the admin API, then writes the mock backend's fixtures.

  --url <url>               live server (default $SEMANTIFYR_LIVE_URL)
  --admin-user-header <h>   the header the server reads the admin user from; without it the admin fixture is kept
  --transcript <file>       write the raw exchanges here, or with --replay read them from here
  --replay                  build the fixtures from --transcript without a browser`

interface RecordedMessage {
  from: 'client' | 'server'
  text: string
}

interface RecordedSession {
  url: string
  messages: RecordedMessage[]
}

interface RecordedRest {
  url: string
  method: string
  status: number
  body: string
}

interface Transcript {
  rest: RecordedRest[]
  sessions: RecordedSession[]
}

const SETTLED_TIMEOUT_MS = 4 * 60 * 1000
const RUN_TIMEOUT_MS = 10 * 60 * 1000

async function settled(page: Page): Promise<void> {
  await page.waitForFunction(() => {
    return document.querySelector('.results') !== null && document.querySelector('.results .initialization') === null
  }, null, { timeout: SETTLED_TIMEOUT_MS })
  await page.waitForTimeout(2500)
}

async function walkExamples(context: BrowserContext, liveUrl: string, address: string): Promise<void> {
  const page = await context.newPage()
  await page.goto(`${liveUrl}${address}`)
  await settled(page)
  const items = page.getByRole('menuitem')
  await page.locator('.example-button').click()
  await items.first().waitFor()
  const count = await items.count()
  await page.keyboard.press('Escape')
  for (let index = 1; index < count; index++) {
    const before = page.url()
    await page.locator('.example-button').click()
    await items.nth(index).click()
    await page.waitForURL((url) => {
      return url.href !== before
    })
    await settled(page)
    console.log(`${page.url()}: ${await page.locator('.results .case-row').count()} cases`)
  }
  await page.close()
}

async function verifyAndRead(context: BrowserContext, liveUrl: string, address: string): Promise<void> {
  const page = await context.newPage()
  await page.goto(`${liveUrl}${address}`)
  await settled(page)
  await page.locator('.results').getByRole('button', { name: 'Verify all', exact: true }).click()
  await page.waitForFunction(() => {
    const rows = [...document.querySelectorAll('.results .case-row')]
    return rows.length > 0 && rows.every((row) => {
      return /status-(passed|failed|inconclusive|errored|timeout|not_supported)\b/.test(row.className)
    }) && document.querySelector('.validation-mark.is-validating') === null
  }, null, { timeout: RUN_TIMEOUT_MS, polling: 1000 })
  await page.waitForTimeout(2000)
  const rows = page.locator('.results .case-row')
  for (let row = 0; row < await rows.count(); row++) {
    await rows.nth(row).click()
    const views = page.locator('section.trace .trace-views button')
    for (let view = 0; view < await views.count(); view++) {
      await views.nth(view).click()
      await page.waitForTimeout(1000)
    }
  }
  const documents = page.getByRole('button', { name: 'Read-only documents' })
  await documents.click({ force: true })
  const entries = await page.locator('.documents-menu .menu-item:not([disabled])').count()
  await page.keyboard.press('Escape')
  for (let index = 0; index < entries; index++) {
    await documents.click({ force: true })
    await page.locator('.documents-menu .menu-item:not([disabled])').nth(index).click({ timeout: 5000 }).catch(async () => {
      await page.keyboard.press('Escape')
    })
    await page.waitForTimeout(1000)
  }
  console.log(`${page.url()}: verified and read`)
  await page.close()
}

async function cancelRuns(context: BrowserContext, liveUrl: string, address: string): Promise<void> {
  const page = await context.newPage()
  await page.goto(`${liveUrl}${address}`)
  await settled(page)
  await page.locator('.results').getByRole('button', { name: 'Verify all', exact: true }).click()
  await page.waitForTimeout(1500)
  await page.locator('.results .button-cancel').click()
  await page.waitForTimeout(3000)
  const row = page.locator('.results .case-row').nth(2)
  await row.getByRole('button', { name: /^Run / }).click()
  await row.getByRole('button', { name: /^Cancel / }).click({ timeout: 5000 }).catch(() => {})
  await page.waitForTimeout(3000)
  await page.close()
}

async function recordAdmin(liveUrl: string, header: string): Promise<AdminFixture> {
  const get = async <T>(path: string): Promise<T> => {
    const answer = await fetch(`${liveUrl}${path}`, { headers: { [header]: 'recorder' } })
    if (!answer.ok) {
      throw new Error(`${path} answered ${answer.status}`)
    }
    return await answer.json() as T
  }
  const recorded: AdminFixture = {
    whoami: await get('/api/admin/whoami'),
    config: await get('/api/admin/config'),
    status: await get('/api/admin/status'),
  }
  // the server's directories are the recording machine's; the fixture names those of the image
  const { server, sessionManager } = recorded.config
  const directories = new Map([
    [server.webRootDirectory, '/opt/semantifyr/staging/web'],
    [sessionManager.semanticLibrariesDirectory, '/opt/semantifyr/staging/semantic-libraries'],
    [sessionManager.rootWorkDirectory, '/tmp/semantifyr'],
  ])
  let text = JSON.stringify(recorded)
  for (const [local, image] of directories) {
    if (local !== null) {
      text = text.replaceAll(local, image)
    }
  }
  return JSON.parse(text) as AdminFixture
}

async function record(liveUrl: string, adminUserHeader: string | undefined): Promise<{ transcript: Transcript, admin: AdminFixture | null }> {
  const executablePath = process.env.SCREEN_TEST_CHROMIUM
  const browser = await chromium.launch(executablePath ? { executablePath } : {})
  const context = await browser.newContext({ viewport: { width: 1440, height: 2400 }, serviceWorkers: 'block' })
  const transcript: Transcript = { rest: [], sessions: [] }
  await context.routeWebSocket(/\/ws\/lsp\//, (socket) => {
    const server = socket.connectToServer()
    const session: RecordedSession = { url: socket.url(), messages: [] }
    transcript.sessions.push(session)
    socket.onMessage((message) => {
      session.messages.push({ from: 'client', text: message.toString() })
      server.send(message)
    })
    server.onMessage((message) => {
      session.messages.push({ from: 'server', text: message.toString() })
      socket.send(message)
    })
  })
  await context.route(/\/api\//, async (route) => {
    const answer = await route.fetch()
    const body = await answer.text()
    transcript.rest.push({ url: route.request().url(), method: route.request().method(), status: answer.status(), body })
    await route.fulfill({ response: answer, body })
  })
  const attempt = async (scenario: Promise<void>): Promise<void> => {
    await scenario.catch((error: unknown) => {
      console.error('a scenario failed, the recording goes on without it', error)
    })
  }
  await attempt(cancelRuns(context, liveUrl, '/oxsts/advanced'))
  for (const address of ['/oxsts', '/oxsts?library=gamma', '/oxsts?library=sysml', '/gamma', '/sysml']) {
    await attempt(walkExamples(context, liveUrl, address))
  }
  let admin: AdminFixture | null = null
  const running = context.newPage().then(async (page) => {
    const embed = await context.newPage()
    await embed.goto(`${liveUrl}/gamma/level-crossing?view=embed`)
    await page.goto(`${liveUrl}/oxsts/advanced`)
    await settled(page)
    await page.locator('.results').getByRole('button', { name: 'Verify all', exact: true }).click()
    if (adminUserHeader !== undefined) {
      const started = Date.now()
      do {
        admin = await recordAdmin(liveUrl, adminUserHeader)
      } while (!admin.status.sessions.some((session) => {
        return session.activeVerifications.length > 0
      }) && Date.now() - started < SETTLED_TIMEOUT_MS)
    }
    await page.locator('.results .button-cancel').click()
    await embed.close()
    await page.close()
  })
  await attempt(running)
  for (const address of ['/oxsts/basics', '/oxsts/level-crossing', '/oxsts/level-crossing?library=gamma', '/oxsts/level-crossing?library=sysml', '/gamma/level-crossing', '/sysml/level-crossing']) {
    await attempt(verifyAndRead(context, liveUrl, address))
  }
  await browser.close()
  const info = await fetch(`${liveUrl}/api/info`)
  transcript.rest.push({ url: info.url, method: 'GET', status: info.status, body: await info.text() })
  return { transcript, admin }
}

interface Pending {
  message: RequestMessage
  key: string | null
}

function emptyFixture(): FlavorFixture {
  return { initialize: null, sessionInfo: null, documents: {}, results: {}, validations: {}, reads: {}, verifyProgress: [], validateProgress: [] }
}

function sameStart(left: Range, right: Range): boolean {
  return left.start.line === right.start.line && left.start.character === right.start.character
}

// the answers the mock replays, filed by flavor and by the text of the document they were given for
function fixturesOf(transcript: Transcript): Map<string, FlavorFixture> {
  const fixtures = new Map<string, FlavorFixture>()
  for (const session of transcript.sessions) {
    const flavorId = decodeURIComponent(new URL(session.url).pathname.split('/').at(-1) ?? '')
    let fixture = fixtures.get(flavorId)
    if (!fixture) {
      fixture = emptyFixture()
      fixtures.set(flavorId, fixture)
    }
    const texts = new Map<string, string>()
    const discovered = new Map<string, VerificationCaseSpecification[]>()
    const pending = new Map<MessageId, Pending>()
    const progress = new Map<string, ProgressValue[]>()
    const progressOf = new Map<MessageId, string>()
    const documentOf = (uri: string) => {
      const text = texts.get(uri)
      if (text === undefined) {
        return null
      }
      const key = textKey(text)
      fixture.documents[key] ??= {}
      return fixture.documents[key]
    }
    const caseAt = (uri: string, range: Range): string | null => {
      return discovered.get(uri)?.find((candidate) => {
        return sameStart(candidate.location.range, range)
      })?.id ?? null
    }
    let lastRun: { id: MessageId, kind: 'verify' | 'validate' } | null = null
    for (const recorded of session.messages) {
      const message: Message = parseMessage(recorded.text)
      if (recorded.from === 'client') {
        if (isNotification(message)) {
          if (message.method === 'textDocument/didOpen') {
            const { textDocument } = message.params as { textDocument: { uri: string, text: string } }
            texts.set(textDocument.uri, textDocument.text)
          }
          if (message.method === 'textDocument/didChange') {
            const { textDocument, contentChanges } = message.params as { textDocument: { uri: string }, contentChanges: { text: string }[] }
            texts.set(textDocument.uri, contentChanges.at(-1)?.text ?? '')
          }
        } else if (isRequest(message)) {
          const argument = (message.params as { arguments?: unknown[] } | undefined)?.arguments?.[0]
          const uri = typeof argument === 'string' ? argument : (argument as { uri?: string } | undefined)?.uri
          pending.set(message.id, { message, key: uri !== undefined && texts.has(uri) ? textKey(texts.get(uri) ?? '') : null })
          const command = (message.params as { command?: string } | undefined)?.command ?? ''
          if (command.endsWith('.case.verify')) {
            lastRun = { id: message.id, kind: 'verify' }
          } else if (command.endsWith('.validateWitness') || command.endsWith('.witness.validate')) {
            lastRun = { id: message.id, kind: 'validate' }
          }
        }
        continue
      }
      if (isNotification(message)) {
        if (message.method === 'textDocument/publishDiagnostics') {
          const { uri, diagnostics } = message.params as { uri: string, diagnostics: Diagnostic[] }
          const document = documentOf(uri)
          if (document) {
            document.diagnostics = diagnostics
          }
        }
        if (message.method === '$/progress') {
          const { token, value } = message.params as { token: string, value: ProgressValue }
          progress.get(token)?.push(value)
        }
        continue
      }
      if (isRequest(message)) {
        if (message.method === 'window/workDoneProgress/create' && lastRun !== null) {
          const { token } = message.params as { token: string }
          progress.set(token, [])
          progressOf.set(lastRun.id, token)
        }
        continue
      }
      const request = pending.get(message.id ?? -1)
      if (!request || message.error !== undefined) {
        continue
      }
      pending.delete(message.id ?? -1)
      const { method, params } = request.message
      if (method === 'initialize') {
        fixture.initialize = message.result
      } else if (method === 'semantifyr/live/session/info') {
        fixture.sessionInfo = message.result as FlavorFixture['sessionInfo']
      } else if (method === 'semantifyr/live/session/document/read') {
        fixture.reads[(params as { uri: string }).uri] = (message.result as { text: string | null } | null)?.text ?? null
      } else if (method === 'workspace/executeCommand') {
        const { command, arguments: commandArguments } = params as { command: string, arguments: unknown[] }
        if (command.endsWith('.case.discover')) {
          const uri = commandArguments[0] as string
          const cases = message.result as VerificationCaseSpecification[]
          discovered.set(uri, cases)
          if (request.key !== null) {
            fixture.documents[request.key] = { ...fixture.documents[request.key], cases }
          }
        } else if (command.endsWith('.compiled.oxsts')) {
          if (request.key !== null) {
            fixture.documents[request.key] = { ...fixture.documents[request.key], compiled: message.result as CompiledOxstsResult }
          }
        } else {
          const location = commandArguments[0] as { uri: string, range: Range }
          const caseId = caseAt(location.uri, location.range)
          const token = progressOf.get(message.id ?? -1)
          const values = token === undefined ? [] : progress.get(token) ?? []
          if (command.endsWith('.case.verify')) {
            const result = message.result as VerificationCaseResult
            if (caseId !== null && result.status !== 'errored') {
              fixture.results[caseId] = result
            }
            if (values.at(-1)?.kind === 'end' && values.length > fixture.verifyProgress.length) {
              fixture.verifyProgress = values
            }
          } else {
            const result = message.result as WitnessValidationResult
            if (result.status !== 'errored') {
              fixture.validations[caseId ?? location.uri] = result
            }
            if (values.at(-1)?.kind === 'end' && values.length > fixture.validateProgress.length) {
              fixture.validateProgress = values
            }
          }
        }
      }
    }
  }
  return fixtures
}

function restOf(transcript: Transcript): RestFixture {
  const bodyOf = (path: string): unknown => {
    const found = transcript.rest.findLast((call) => {
      return call.method === 'GET' && new URL(call.url).pathname === path && call.status === 200
    })
    if (!found) {
      throw new Error(`the transcript has no answer of ${path}`)
    }
    return JSON.parse(found.body)
  }
  return {
    info: bodyOf('/api/info') as RestFixture['info'],
    flavors: bodyOf('/api/flavors') as RestFixture['flavors'],
    portfolios: bodyOf('/api/portfolios') as RestFixture['portfolios'],
    libraries: bodyOf('/api/libraries') as RestFixture['libraries'],
  }
}

function write(name: string, value: unknown): void {
  writeFileSync(join(FIXTURES_DIRECTORY, name), `${JSON.stringify(value, null, 1)}\n`)
}

const { values } = parseArgs({
  options: {
    'url': { type: 'string' },
    'admin-user-header': { type: 'string' },
    'transcript': { type: 'string' },
    'replay': { type: 'boolean', default: false },
    'help': { type: 'boolean', default: false },
  },
})
if (values.help) {
  console.log(usage)
  process.exit(0)
}
let transcript: Transcript
if (values.replay) {
  if (values.transcript === undefined) {
    throw new Error('--replay reads the transcript named by --transcript')
  }
  transcript = JSON.parse(readFileSync(values.transcript, 'utf-8')) as Transcript
} else {
  const liveUrl = (values.url ?? process.env.SEMANTIFYR_LIVE_URL ?? '').replace(/\/$/, '')
  if (liveUrl === '') {
    throw new Error('name the live server with --url or SEMANTIFYR_LIVE_URL')
  }
  const recorded = await record(liveUrl, values['admin-user-header'])
  transcript = recorded.transcript
  if (values.transcript !== undefined) {
    writeFileSync(values.transcript, JSON.stringify(transcript))
  }
  if (recorded.admin !== null) {
    write('admin.json', recorded.admin)
  }
}
write('rest.json', restOf(transcript))
for (const [flavorId, fixture] of fixturesOf(transcript)) {
  write(`${flavorId}.json`, fixture)
}
