/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import { describe, expect, it, vi } from 'vitest'
import { liveFlavors } from '../examples'
import { fakeModelDocuments } from './helpers/live'
import { servedFlavors } from './fixtures/flavors'

const workers = vi.hoisted(() => {
  return { started: 0 }
})

vi.mock('vscode', () => {
  return {}
})

vi.mock('../lib/session/monaco', () => {
  return {
    initializeMonaco: async () => {},
    connectLanguageClient: async () => {
      return {
        getLanguageClient: () => undefined,
        dispose: async () => {},
      }
    },
    connectWorkerLanguageClient: async () => {
      workers.started++
      return {
        isStarted: () => true,
        getLanguageClient: () => undefined,
        dispose: async () => {},
      }
    },
  }
})

const { createLanguageRegistry } = await import('../lib/language/registry')

describe('the SysML language', () => {
  it('is one unit of the registry beside a served unit per backend language, the library flavors under OXSTS', () => {
    const registry = createLanguageRegistry(liveFlavors(servedFlavors))

    expect(registry.units.map((unit) => {
      return unit.id
    })).toEqual(['oxsts', 'gamma', 'sysml'])
    expect(registry.unitOf('oxsts-with-gamma-library')?.flavors.map((flavor) => {
      return flavor.id
    })).toEqual([
      'oxsts',
      'oxsts-with-gamma-library',
      'oxsts-with-sysmlv2-library',
    ])
    expect(registry.unitOf('sysml')?.flavors.map((flavor) => {
      return flavor.id
    })).toEqual(['sysml'])
  })

  it('starts its worker once for the visit, however many runtimes it starts', async () => {
    const registry = createLanguageRegistry(liveFlavors([]))
    const unit = registry.unitOf('sysml')!
    const flavor = unit.flavors[0]!
    const context = {
      backendUrl: 'http://localhost:8080',
      documentUri: 'file:///workspace/snippet.sysml',
      colorMode: 'dark' as const,
      modelDocuments: fakeModelDocuments(),
    }

    const first = unit.start(flavor, context)
    await vi.waitFor(() => {
      expect(first.connection.get().status).toBe('connected')
    })
    await first.dispose()
    const second = unit.start(flavor, context)
    await vi.waitFor(() => {
      expect(second.connection.get().status).toBe('connected')
    })

    expect(workers.started).toBe(1)
    expect(second.initialization.get().steps.find((step) => {
      return step.id === 'language-server'
    })?.state).toBe('done')
    await second.dispose()
  })
})
