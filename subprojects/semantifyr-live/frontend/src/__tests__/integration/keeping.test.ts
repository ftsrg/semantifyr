/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import { afterEach, describe, expect, it, vi } from 'vitest'
import { encodeCompressedBase64Url, type VerificationCaseSpecification } from '@semantifyr/editor-common'

import type { LspClient } from '../../lib/verification/engine'
import type { DocumentFile, FileAccess } from '../../lib/workspace/files'
import { saveDocument, saveDocumentAs } from '../../lib/workspace/keeping'
import { createTestLive, testFlavors, type TestLive } from '../helpers/live'
import { failedResult, passedResult } from '../fixtures/verification-results'

vi.mock('vscode', () => {
  return {}
})

vi.mock('../../lib/session/monaco', () => {
  return {}
})

// Goal: what a visit keeps of a piece of work: which documents are unsaved, what saving does where the browser allows and
// where it does not, which language a file opens in, and what a link with results starts

const MODEL_URI = 'file:///workspace/snippet.oxsts'

function caseAt(id: string, line: number): VerificationCaseSpecification {
  return {
    id,
    label: id,
    location: { uri: MODEL_URI, range: { start: { line, character: 0 }, end: { line, character: 1 } } },
  }
}

function answering(cases: VerificationCaseSpecification[], verified: number[] = []): LspClient {
  return {
    sendRequest: async (method, parameters) => {
      if (method !== 'workspace/executeCommand') {
        return null
      }
      const { command, arguments: commandArguments } = parameters as { command: string, arguments: [{ range: { start: { line: number } } }] }
      if (command === 'oxsts.case.discover') {
        return cases
      }
      if (command === 'oxsts.case.verify') {
        const line = commandArguments[0].range.start.line
        verified.push(line)
        return line === 1 ? failedResult : passedResult
      }
      return null
    },
    sendNotification: () => {},
  }
}

async function openConnected(search = '', client = answering([])): Promise<TestLive> {
  const test = createTestLive(client)
  test.live.start(search)
  await vi.waitFor(() => {
    expect(test.live.workspace.get().runtime).not.toBeNull()
  })
  const record = test.live.documents.get().get(test.live.workspace.get().documentKey!)!
  test.models.open(record.uri, record.text)
  test.runtime().connect()
  return test
}

function recordingAccess(overrides: Partial<FileAccess> = {}): FileAccess & { downloads: { name: string, text: string }[] } {
  const downloads: { name: string, text: string }[] = []
  return {
    pick: async () => {
      return null
    },
    saveAs: null,
    download: (name, text) => {
      downloads.push({ name, text })
    },
    confirmDiscard: () => {
      return true
    },
    ...overrides,
    downloads,
  }
}

describe('the unsaved state of the visit', () => {
  let open: TestLive | null = null

  afterEach(async () => {
    await open?.live.dispose()
    open = null
    window.localStorage.clear()
  })

  it('follows the edits, a save, and an example switch that leaves the edits behind', async () => {
    open = await openConnected()
    const [first, second] = testFlavors()[0].examples
    expect(open.live.unsaved.get()).toBe(false)

    open.models.edit(MODEL_URI, 'edited model')
    expect(open.live.unsaved.get()).toBe(true)
    expect(open.live.hasUnsaved()).toBe(true)

    open.live.markSaved(null, 'edited model')
    expect(open.live.unsaved.get()).toBe(false)
    expect(open.live.hasUnsaved()).toBe(false)

    open.models.edit(MODEL_URI, 'edited again')
    open.live.selectModel('oxsts', second!.id)
    expect(open.live.unsaved.get()).toBe(false)
    expect(open.live.hasUnsaved()).toBe(true)

    open.live.selectModel('oxsts', first!.id)
    expect(open.live.unsaved.get()).toBe(true)
  })

  it('opens a file as the shared model, named by the file and unsaved until its first edit is saved', async () => {
    open = await openConnected()
    const written: string[] = []

    open.live.openFile({
      name: 'crossing.oxsts',
      text: 'file text',
      write: async (text) => {
        written.push(text)
      },
    })

    const record = open.live.documents.get().get(open.live.workspace.get().documentKey!)!
    expect(record).toMatchObject({ shared: true, text: 'file text', file: { name: 'crossing.oxsts' } })
    expect(open.models.read(MODEL_URI)).toBe('file text')
    expect(open.live.unsaved.get()).toBe(false)

    open.models.edit(MODEL_URI, 'file text, edited')
    expect(open.live.unsaved.get()).toBe(true)
    expect(await saveDocument(open.live, recordingAccess(), 'ignored.oxsts')).toBe('written')
    expect(written).toEqual(['file text, edited'])
    expect(open.live.unsaved.get()).toBe(false)
  })

  it('opens a file of another language by switching to it', async () => {
    open = await openConnected()

    open.live.openFile({ name: 'LevelCrossing.gamma', text: 'gamma text', write: null })

    expect(open.live.workspace.get().flavor?.id).toBe('gamma')
    expect(open.runtimes).toHaveLength(2)
  })

  it('writes neither the model nor a verdict to the browser\'s storage', async () => {
    open = await openConnected('', answering([caseAt('a', 1)]))
    open.live.openFile({ name: 'opened.oxsts', text: 'file text', write: null })
    open.models.edit(MODEL_URI, 'edited model')
    open.live.markSaved(null, 'edited model')
    open.live.verifyAll()
    await vi.waitFor(() => {
      expect(open!.live.verification.get().get('oxsts#shared')?.phase).toBe('done')
    })

    const stored = [window.localStorage, window.sessionStorage].flatMap((storage) => {
      return Object.keys(storage).map((key) => {
        return `${key}=${storage.getItem(key) ?? ''}`
      })
    })

    expect(stored.join('\n')).not.toMatch(/edited model|file text|passed|failed/)
  })

  it('drops the verdicts of the document a file replaces', async () => {
    open = await openConnected('', answering([caseAt('a', 1)]))
    open.live.openFile({ name: 'first.oxsts', text: 'first', write: null })
    await vi.waitFor(() => {
      expect(open!.live.verification.get().get('oxsts#shared')?.cases).toHaveLength(1)
    })

    open.live.openFile({ name: 'second.oxsts', text: 'second', write: null })

    expect(open.live.verification.get().get('oxsts#shared')?.cases ?? []).toHaveLength(0)
  })
})

describe('saving', () => {
  let open: TestLive | null = null

  afterEach(async () => {
    await open?.live.dispose()
    open = null
    window.localStorage.clear()
  })

  it('goes through the save dialog where the browser has one, then back into that file', async () => {
    open = await openConnected()
    open.models.edit(MODEL_URI, 'edited model')
    const written: string[] = []
    const file: DocumentFile = {
      name: 'chosen.oxsts',
      write: async (text) => {
        written.push(text)
      },
    }
    const access = recordingAccess({
      saveAs: async () => {
        return file
      },
    })

    expect(await saveDocument(open.live, access, 'suggested.oxsts')).toBe('saved')
    expect(open.live.unsaved.get()).toBe(false)
    open.models.edit(MODEL_URI, 'edited again')
    expect(await saveDocument(open.live, access, 'suggested.oxsts')).toBe('written')
    expect(written).toEqual(['edited again'])
  })

  it('downloads where the browser has no way back into a file, under the file\'s name', async () => {
    open = await openConnected()
    open.live.openFile({ name: 'opened.oxsts', text: 'file text', write: null })
    open.models.edit(MODEL_URI, 'edited model')
    const access = recordingAccess()

    expect(await saveDocument(open.live, access, 'suggested.oxsts')).toBe('downloaded')
    expect(access.downloads).toEqual([{ name: 'opened.oxsts', text: 'edited model' }])
    expect(open.live.unsaved.get()).toBe(false)
  })

  it('asks for a place on Save as even when a file is behind the document, and that file is the document\'s from then on', async () => {
    open = await openConnected()
    const old: string[] = []
    const chosen: string[] = []
    open.live.openFile({
      name: 'old.oxsts',
      text: 'file text',
      write: async (text) => {
        old.push(text)
      },
    })
    open.models.edit(MODEL_URI, 'edited model')
    const access = recordingAccess({
      saveAs: async () => {
        return {
          name: 'new.oxsts',
          write: async (text) => {
            chosen.push(text)
          },
        }
      },
    })

    expect(await saveDocumentAs(open.live, access, 'suggested.oxsts')).toBe('saved')
    expect(old).toEqual([])
    expect(open.live.unsaved.get()).toBe(false)
    expect(open.live.documents.get().get(open.live.workspace.get().documentKey!)?.file?.name).toBe('new.oxsts')
    open.models.edit(MODEL_URI, 'edited again')
    expect(await saveDocument(open.live, access, 'suggested.oxsts')).toBe('written')
    expect(chosen).toEqual(['edited again'])
  })

  it('downloads on Save as where the browser has no save dialog, under the suggested name', async () => {
    open = await openConnected()
    open.models.edit(MODEL_URI, 'edited model')
    const access = recordingAccess()

    expect(await saveDocumentAs(open.live, access, 'suggested.oxsts')).toBe('downloaded')
    expect(access.downloads).toEqual([{ name: 'suggested.oxsts', text: 'edited model' }])
    expect(open.live.unsaved.get()).toBe(false)
  })

  it('stays unsaved when the Save as dialog is cancelled', async () => {
    open = await openConnected()
    open.models.edit(MODEL_URI, 'edited model')
    const access = recordingAccess({
      saveAs: async () => {
        return null
      },
    })

    expect(await saveDocumentAs(open.live, access, 'suggested.oxsts')).toBe('cancelled')
    expect(open.live.unsaved.get()).toBe(true)
  })

  it('stays unsaved when the save dialog is cancelled', async () => {
    open = await openConnected()
    open.models.edit(MODEL_URI, 'edited model')
    const access = recordingAccess({
      saveAs: async () => {
        return null
      },
    })

    expect(await saveDocument(open.live, access, 'suggested.oxsts')).toBe('cancelled')
    expect(open.live.unsaved.get()).toBe(true)
  })

  it('falls back to the save dialog when the browser refuses the write into the file', async () => {
    open = await openConnected()
    open.live.openFile({
      name: 'opened.oxsts',
      text: 'file text',
      write: async () => {
        throw new DOMException('refused', 'NotAllowedError')
      },
    })
    const access = recordingAccess()

    expect(await saveDocument(open.live, access, 'suggested.oxsts')).toBe('downloaded')
  })
})

describe('a link with results', () => {
  let open: TestLive | null = null

  afterEach(async () => {
    await open?.live.dispose()
    open = null
    window.localStorage.clear()
  })

  it('starts the verification by itself and keeps the sharer\'s selected case selected when the run ends', async () => {
    const verified: number[] = []
    const encoded = await encodeCompressedBase64Url(JSON.stringify([['a', 'passed'], ['b', 'passed']]))
    const search = `/oxsts#case=a&verdicts=${encoded}`
    open = createTestLive(answering([caseAt('a', 1), caseAt('b', 2)], verified))
    open.live.start(search)
    await vi.waitFor(() => {
      expect(open!.live.workspace.get().runtime).not.toBeNull()
    })
    expect(open.live.sharedResults.get()?.verdicts.get('a')).toBe('passed')
    expect(open.live.pendingVerifyAll.get()).toBe(true)

    open.models.open(MODEL_URI, open.live.currentText()!)
    open.runtime().connect()

    await vi.waitFor(() => {
      expect(verified).toEqual([1, 2])
    })
    await vi.waitFor(() => {
      expect(open!.live.verification.get().get(open!.live.workspace.get().documentKey!)?.phase).toBe('done')
    })
    expect(open.live.selection.get()).toBe('a')
  })

  it('forgets the sharer\'s verdicts when the model is edited', async () => {
    const encoded = await encodeCompressedBase64Url(JSON.stringify([['a', 'passed']]))
    open = createTestLive(answering([caseAt('a', 1)]))
    open.live.start(`/oxsts#verdicts=${encoded}`)
    await vi.waitFor(() => {
      expect(open!.live.sharedResults.get()).not.toBeNull()
    })
    open.models.open(MODEL_URI, open.live.currentText()!)

    open.models.edit(MODEL_URI, 'edited model')

    expect(open.live.sharedResults.get()).toBeNull()
  })

  it('opens the model and says so when the results cannot be read', async () => {
    open = createTestLive(answering([caseAt('a', 1)]))
    open.live.start('/oxsts#verdicts=garbled')
    await vi.waitFor(() => {
      expect(open!.live.workspace.get().runtime).not.toBeNull()
    })

    expect(open.live.address.get()?.kind).toBe('results')
    expect(open.live.sharedResults.get()).toBeNull()
    expect(open.live.pendingVerifyAll.get()).toBe(false)
  })

  it('opens as it does today without results', async () => {
    open = createTestLive(answering([]))
    open.live.start('/oxsts')
    await vi.waitFor(() => {
      expect(open!.live.workspace.get().runtime).not.toBeNull()
    })

    expect(open.live.address.get()).toBeNull()
    expect(open.live.sharedResults.get()).toBeNull()
  })
})
