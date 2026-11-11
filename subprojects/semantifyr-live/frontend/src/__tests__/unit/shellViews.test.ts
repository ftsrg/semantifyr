/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import { describe, expect, it, vi } from 'vitest'
import type { VerificationCaseSpecification } from '@semantifyr/editor-common'

import { editorColorsFrom, toHexColor } from '../../lib/session/editorTheme'
import { fileUriOfLibrary, isLibraryFileUri, libraryUriOf } from '../../lib/session/readOnlyDocuments'
import { connectionConnected, connectionLost, INITIAL_CONNECTION } from '../../lib/stores/connection'
import { finishStep, initializationOf, InitializationSteps, startStep } from '../../lib/stores/initialization'
import { createDemandStore } from '../../lib/stores/store'
import type { VerificationCaseState, VerificationState } from '../../lib/verification/engine'
import { caseRowOf, countsOf, countsPhrase } from '../../lib/workspace/caseRows'
import { connectionView } from '../../lib/workspace/connectionView'
import { createDocumentTabs } from '../../lib/workspace/documentTabs'
import { libraryTabOf } from '../../lib/workspace/libraryTabs'
import { portfolioChoices, portfolioName } from '../../lib/workspace/portfolioChoices'
import { CLOSING_LINE_MS, shellStatus, stepTimeText, type ShellStatusInput } from '../../lib/workspace/shellStatus'

vi.mock('vscode', () => {
  return {}
})

vi.mock('../../lib/session/monaco', () => {
  return {}
})

function caseAt(id: string, line: number): VerificationCaseSpecification {
  return {
    id: `example::${id}`,
    label: `example::${id}`,
    location: { uri: 'file:///workspace/snippet.oxsts', range: { start: { line, character: 0 }, end: { line, character: 1 } } },
  }
}

function result(id: string, status: VerificationCaseState['status'], extra: Partial<VerificationCaseState> = {}): VerificationCaseState {
  return { caseInfo: caseAt(id, 1), status, resultStatus: status, metrics: { totalDuration: 'PT2.9S', preparationDuration: 'PT0S', verificationDuration: 'PT0S', backAnnotationDuration: 'PT0S' }, ...extra }
}

function statusInput(): ShellStatusInput {
  return {
    catalog: 'ready',
    languageName: 'SysML v2',
    connection: connectionConnected(INITIAL_CONNECTION, 0),
    initialization: READY,
    verification: { phase: 'idle', discovery: 'discovered', cases: [] },
    timing: null,
    queued: null,
    blockedErrors: null,
    now: 10_000,
  }
}

const STEPS = initializationOf([
  InitializationSteps.connect,
  InitializationSteps.initialize,
  InitializationSteps.cases,
])

const CASES_KNOWN = startStep(finishStep(finishStep(STEPS, InitializationSteps.connect.id, 0), InitializationSteps.cases.id, 0), InitializationSteps.initialize.id, 0)

const READY = finishStep(CASES_KNOWN, InitializationSteps.initialize.id, 0)

function statusOf(input: Partial<ShellStatusInput>): string {
  return shellStatus({ ...statusInput(), ...input }).text
}

describe('a case row', () => {
  it('names the verdict whatever the property, by the case\'s short name', () => {
    expect(caseRowOf(result('a', 'failed'))).toMatchObject({ name: 'a', status: 'failed', word: 'Failed', time: '2.9 s' })
    expect(caseRowOf(result('a', 'timeout'))).toMatchObject({ status: 'inconclusive', word: 'Inconclusive', message: 'timed out' })
    expect(caseRowOf(result('a', 'not_supported', { message: 'not supported: unbounded' }))).toMatchObject({ status: 'inconclusive', message: 'not supported: unbounded' })
    expect(caseRowOf(result('a', 'errored', { message: 'broke' }))).toMatchObject({ status: 'error', word: 'Error', message: 'broke' })
    expect(caseRowOf({ caseInfo: caseAt('a', 1), status: 'stale', message: 'Verification cancelled' })).toMatchObject({ status: 'not-run', word: 'Not run', message: 'cancelled', time: null })
  })

  it('marks a result outdated and its validation, but not a case that waits', () => {
    expect(caseRowOf(result('a', 'passed', { outdated: true, witnessValidation: 'valid' }))).toMatchObject({ outdated: true, validation: 'validated' })
    expect(caseRowOf(result('a', 'passed', { validating: true, validationServerState: 'Running' }))).toMatchObject({ validation: 'validating' })
    expect(caseRowOf(result('a', 'passed', { witnessValidation: 'invalid' }))).toMatchObject({ validation: 'validation-failed' })
    expect(caseRowOf(result('a', 'running', { outdated: true }))).toMatchObject({ outdated: false, validation: null, time: null })
  })

  it('keeps the verdict of a case run again, with its trace and its count, until the new one arrives', () => {
    const trace = { callTrace: { initializedStep: { traces: [] }, steps: [] }, witnessState: { initializedStep: { values: [] }, steps: [] }, witnessUri: null }
    const rows = [
      result('a', 'failed', { status: 'running', serverState: 'Running', trace }),
      result('b', 'passed', { status: 'queued' }),
      { caseInfo: caseAt('c', 1), status: 'queued' as const },
    ].map(caseRowOf)

    expect(rows[0]).toMatchObject({ status: 'failed', word: 'Failed', time: '2.9 s', hasTrace: true, waiting: true, rerun: 'running' })
    expect(rows[1]).toMatchObject({ status: 'passed', waiting: true, rerun: 'queued' })
    expect(rows[2]).toMatchObject({ status: 'queued', waiting: true, rerun: null })
    expect(countsOf(rows)).toEqual({ passed: 1, failed: 1, inconclusive: 0, error: 0, waiting: 1, total: 3 })
  })

  it('reads queued for a case sent to the server until the server reports it running, and running from then', () => {
    expect(caseRowOf({ caseInfo: caseAt('a', 1), status: 'running' })).toMatchObject({ status: 'queued', word: 'Queued', waiting: true })
    expect(caseRowOf({ caseInfo: caseAt('a', 1), status: 'running', serverState: 'Queued' })).toMatchObject({ status: 'queued', word: 'Queued' })
    expect(caseRowOf({ caseInfo: caseAt('a', 1), status: 'running', serverState: 'Running' })).toMatchObject({ status: 'running', word: 'Running' })
    expect(caseRowOf(result('a', 'failed', { status: 'running', serverState: 'Queued' }))).toMatchObject({ status: 'failed', rerun: 'queued' })
  })

  it('times a result by the server\'s metrics, else from when the server ran it', () => {
    expect(caseRowOf({ caseInfo: caseAt('a', 1), status: 'timeout', resultStatus: 'timeout', ranMilliseconds: 60_400 })).toMatchObject({ time: '60 s' })
    expect(caseRowOf(result('a', 'passed', { ranMilliseconds: 60_400 }))).toMatchObject({ time: '2.9 s' })
  })

  it('marks a validation validating only while the server runs it', () => {
    expect(caseRowOf(result('a', 'failed', { validating: true }))).toMatchObject({ validation: 'validation-queued' })
    expect(caseRowOf(result('a', 'failed', { validating: true, validationServerState: 'Queued' }))).toMatchObject({ validation: 'validation-queued' })
    expect(caseRowOf(result('a', 'failed', { validating: true, validationServerState: 'Running' }))).toMatchObject({ validation: 'validating' })
  })

  it('counts the verdicts and the waiting cases, and says the failed first', () => {
    const counts = countsOf([
      result('a', 'passed'),
      result('b', 'failed'),
      result('c', 'queued'),
      result('d', 'stale'),
    ].map(caseRowOf))
    expect(counts).toEqual({ passed: 1, failed: 1, inconclusive: 0, error: 0, waiting: 1, total: 4 })
    expect(countsPhrase(counts)).toBe('1 failed, 1 passed')
  })
})

describe('the status line', () => {
  it('reports a run with how many cases are done, closes it on its time and counts, and returns to rest', () => {
    const running: VerificationState = { phase: 'running', discovery: 'discovered', cases: [result('a', 'running'), result('b', 'passed')] }
    expect(shellStatus({ ...statusInput(), verification: running })).toMatchObject({ kind: 'busy', caseName: 'a', after: ', 1 of 2 done' })

    const done: VerificationState = { phase: 'done', discovery: 'discovered', cases: [result('a', 'failed'), result('b', 'passed')] }
    const timing = { key: 'k', caseId: null, startedAt: 6_300, finishedAt: 10_000 }
    expect(statusOf({ verification: done, timing })).toBe('Done in 3.7 s: 1 failed, 1 passed')
    expect(statusOf({ verification: done, timing, now: 10_000 + CLOSING_LINE_MS })).toBe('Ready: 2 verification cases')
    expect(statusOf({ verification: done, timing: { ...timing, caseId: 'example::b' } })).toBe('Done in 3.7 s: 1 passed')
  })

  it('names the case of a single run without a position', () => {
    const running: VerificationState = { phase: 'running', discovery: 'discovered', cases: [result('a', 'stale'), result('b', 'running')] }
    const timing = { key: 'k', caseId: 'example::b', startedAt: 6_300, finishedAt: null }
    expect(shellStatus({ ...statusInput(), verification: running, timing })).toMatchObject({ kind: 'busy', caseName: 'b', after: '' })
  })

  it('says the server is busy exactly when it queues a case of the session and runs none of them', () => {
    const queuedOnServer: VerificationState = { phase: 'running', discovery: 'discovered', cases: [result('a', 'running', { serverState: 'Queued' }), result('b', 'queued')] }
    expect(shellStatus({ ...statusInput(), verification: queuedOnServer })).toMatchObject({ kind: 'busy', text: 'The server is busy with other verifications: ', caseName: 'a', after: ' waits' })

    const validationRunning: VerificationState = { ...queuedOnServer, cases: [result('a', 'running', { serverState: 'Queued' }), result('b', 'failed', { validating: true, validationServerState: 'Running' })] }
    expect(shellStatus({ ...statusInput(), verification: validationRunning })).toMatchObject({ text: 'Verifying ', caseName: 'a' })

    const notReported: VerificationState = { ...queuedOnServer, cases: [result('a', 'running'), result('b', 'running')] }
    expect(shellStatus({ ...statusInput(), verification: notReported })).toMatchObject({ text: 'Verifying ', caseName: 'a', after: ', 0 of 2 done' })

    const validationQueued: VerificationState = { phase: 'done', discovery: 'discovered', cases: [result('a', 'failed', { validating: true, validationServerState: 'Queued' })] }
    expect(shellStatus({ ...statusInput(), verification: validationQueued })).toMatchObject({ caseName: 'a', after: ' waits to be validated' })
  })

  it('names a case the server runs, not the first one sent, and how many more it runs', () => {
    const verification: VerificationState = { phase: 'running', discovery: 'discovered', cases: [result('a', 'running', { serverState: 'Queued' }), result('b', 'running', { serverState: 'Running' }), result('c', 'running', { serverState: 'Running' })] }
    expect(shellStatus({ ...statusInput(), verification })).toMatchObject({ caseName: 'b', after: ' and 1 more, 0 of 3 done' })
  })

  it('says the results are outdated before it says the model is unsaved', () => {
    const outdated: VerificationState = { phase: 'done', discovery: 'discovered', cases: [result('a', 'failed', { outdated: true }), result('b', 'passed', { outdated: true })] }
    expect(shellStatus({ ...statusInput(), verification: outdated, unsaved: true })).toMatchObject({ text: 'The model changed: 2 results are outdated' })
    const fresh: VerificationState = { phase: 'done', discovery: 'discovered', cases: [result('a', 'failed')] }
    expect(shellStatus({ ...statusInput(), verification: fresh, unsaved: true })).toMatchObject({ unsaved: true })
  })

  it('says why Verify all did not run, and what initializes', () => {
    expect(statusOf({ blockedErrors: 2 })).toBe('Verify all did not run: the model has 2 errors')
    const initializing = startStep(STEPS, InitializationSteps.connect.id, 0)
    expect(statusOf({ initialization: initializing })).toBe('Initializing SysML v2: connecting to the server')
  })

  it('says the language server initializes once the cases are known, and what a queued verification waits for', () => {
    const verification: VerificationState = { phase: 'idle', discovery: 'discovered', cases: [result('a', 'stale')] }
    expect(statusOf({ initialization: CASES_KNOWN, verification })).toBe('Initializing the language server')
    expect(statusOf({ initialization: CASES_KNOWN, verification, queued: { caseId: null } })).toBe('Verify all waits: initializing the language server')
    expect(shellStatus({ ...statusInput(), initialization: CASES_KNOWN, verification, queued: { caseId: 'example::a' } })).toMatchObject({ kind: 'busy', text: 'Verify ', caseName: 'a', after: ' waits: initializing the language server' })
  })

  it('times a step that started after the view\'s clock last ticked from zero, never below', () => {
    const step = startStep(STEPS, InitializationSteps.initialize.id, 10_400).steps[1]!

    expect(stepTimeText(step, 10_000, 'failed')).toBe('0 s')
    expect(stepTimeText(step, 12_500, 'failed')).toBe('2 s')
  })

  it('says when the next attempt to connect comes, for a refusal and for a lost connection', () => {
    const refused = connectionLost(INITIAL_CONNECTION, 'connecting', 'Session limit reached', { reconnecting: true, refused: true, retryAt: 26_000 })
    expect(statusOf({ connection: refused })).toBe('The server refused the session: it is at capacity. Retrying in 16 s; the wait grows with each refusal.')
    const lost = connectionLost(INITIAL_CONNECTION, 'connecting', 'Connection lost', { reconnecting: true, retryAt: 13_400 })
    expect(shellStatus({ ...statusInput(), connection: lost })).toMatchObject({ text: 'Connection lost. Reconnecting in 4 s.', action: 'reconnect' })
    expect(statusOf({ connection: lost, now: 14_000 })).toBe('Connection lost. Reconnecting.')
  })

  it('names session trouble in words and offers what fixes it', () => {
    const terminated = shellStatus({ ...statusInput(), connection: connectionLost(INITIAL_CONNECTION, 'failed', 'Session terminated', { terminated: true }) })
    expect(terminated).toMatchObject({ kind: 'warning', text: 'Session terminated. Not reconnecting until you ask.', action: 'reconnect' })
    expect(statusOf({ connection: connectionLost(INITIAL_CONNECTION, 'disconnected', null) })).toBe('Disconnected. The editor keeps working; verifying needs the connection.')
    expect(statusOf({ catalog: 'waiting' })).toBe('The server is not answering. Retrying. Editing and verifying start once it answers.')
  })
})

describe('the connection indicator', () => {
  it('tells a refusal, a termination and a load apart', () => {
    const refused = connectionLost(INITIAL_CONNECTION, 'connecting', 'Session limit reached', { reconnecting: true, refused: true, retryAt: 16_000 })
    expect(connectionView('ready', refused, true, 'live.example', 'SysML v2')).toMatchObject({ look: 'failure', reason: 'refused, retrying', action: 'reconnect' })
    const terminated = connectionLost(INITIAL_CONNECTION, 'failed', 'Session terminated', { terminated: true })
    expect(connectionView('ready', terminated, true, 'live.example', 'SysML v2')).toMatchObject({ reason: 'terminated', action: 'restart', actionLabel: 'Reconnect' })
    expect(connectionView('ready', connectionConnected(INITIAL_CONNECTION, 0), false, 'live.example', 'SysML v2').look).toBe('busy')
    expect(connectionView('waiting', INITIAL_CONNECTION, false, 'live.example', 'SysML v2')).toMatchObject({ reason: 'retrying', action: null })
  })
})

describe('the portfolios', () => {
  it('offers Automatic and Cross-check, then the single engines the server has', () => {
    const choices = portfolioChoices([
      { id: 'smart-full', displayName: 'Auto', description: '', available: true },
      { id: 'all-agree-full', displayName: 'All agree', description: '', available: true },
      { id: 'theta-full', displayName: 'Theta', description: '', available: true },
      { id: 'nuxmv-ic3-invar', displayName: 'nuXmv', description: '', available: false },
    ])
    expect(choices.combined.map((choice) => {
      return choice.name
    })).toEqual(['Automatic', 'Cross-check'])
    expect(choices.singleEngine.map((choice) => {
      return choice.name
    })).toEqual(['Theta'])
    expect(portfolioName([], null)).toBe('Automatic')
  })
})

describe('the read-only tabs', () => {
  it('opens a document once, shows it and updates its text, and returns to the model when it closes', () => {
    const tabs = createDocumentTabs()
    const document = { id: 'oxsts', label: 'a.oxsts', uri: 'live-derived:/oxsts/a.oxsts', languageId: 'oxsts', banner: '', text: 'one' }
    tabs.open(document)
    tabs.open(document, { start: { line: 1, character: 0 }, end: { line: 1, character: 2 } })
    expect(tabs.state.get()).toMatchObject({ active: 'oxsts', selection: { id: 'oxsts', sequence: 1 } })
    expect(tabs.state.get().tabs).toHaveLength(1)
    tabs.update('oxsts', 'two')
    expect(tabs.state.get().tabs[0]?.text).toBe('two')
    tabs.close('oxsts')
    expect(tabs.state.get()).toMatchObject({ tabs: [], active: null })
  })

  it('names a library file under a scheme of its own, whichever address the language gives it', () => {
    const builtin = 'file:///workspace/.semantifyr/builtin/semantifyr.oxsts'
    expect(isLibraryFileUri(builtin)).toBe(true)
    expect(isLibraryFileUri('file:///workspace/snippet.oxsts')).toBe(false)
    expect(fileUriOfLibrary(libraryUriOf(builtin))).toBe(builtin)
    const worker = `${window.location.origin}/sysml-worker/library/Kernel%20Libraries/ScalarValues.kerml`
    expect(libraryUriOf(worker)).toBe('live-library:/sysml-library/Kernel%20Libraries/ScalarValues.kerml')
    expect(libraryTabOf(libraryUriOf(worker), 'plaintext')).toMatchObject({ label: 'ScalarValues.kerml', languageId: 'sysml', banner: 'From the SysML v2 standard library.' })
  })
})

describe('the editor colours', () => {
  it('take every colour from the tokens, rgba as hex with alpha', () => {
    expect(toHexColor('rgba(60, 50, 30, 0.035)')).toBe('#3c321e09')
    expect(toHexColor(' #fbfaf6')).toBe('#fbfaf6')
    const colors = editorColorsFrom((name) => {
      return name === '--surface-editor' ? '#fbfaf6' : '#000000'
    })
    expect(colors.workbench['editor.background']).toBe('#fbfaf6')
    expect(colors.semanticRules.class).toBe('#000000')
  })
})

describe('the stores', () => {
  it('tell their owner while anyone listens', () => {
    const demands: boolean[] = []
    const store = createDemandStore(0, (demanded) => {
      demands.push(demanded)
    })
    const first = store.subscribe(() => {})
    const second = store.subscribe(() => {})
    first()
    first()
    second()
    expect(demands).toEqual([true, false])
  })
})
