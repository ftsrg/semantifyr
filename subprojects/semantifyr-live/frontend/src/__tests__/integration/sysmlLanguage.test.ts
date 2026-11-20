/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import { describe, expect, it, vi } from 'vitest'
import { liveFlavors } from '../../examples'
import { fakeModelDocuments } from '../helpers/live'
import { servedFlavors } from '../fixtures/flavors'

const workers = vi.hoisted(() => {
  return { started: 0, connecting: Promise.resolve(), sockets: [] as string[], mappingRequests: [] as unknown[] }
})

const MAPPED_OXSTS = 'package Mapped'

vi.mock('vscode', () => {
  return {
    languages: {
      createDiagnosticCollection: () => {
        return {
          set: () => {},
          dispose: () => {},
        }
      },
    },
    Uri: {
      parse: (uri: string) => {
        return uri
      },
    },
  }
})

vi.mock('../../lib/session/monaco', () => {
  return {
    initializeMonaco: async () => {},
    onDiagnosticsOf: () => {
      return () => {}
    },
    connectLanguageClient: async (_backendUrl: string, flavorId: string, _languageId: string, _callbacks: unknown, _sessionKind: string | undefined, sessionLanguage: string) => {
      workers.sockets.push(`${flavorId} ${sessionLanguage}`)
      await workers.connecting
      return {
        getLanguageClient: () => undefined,
        dispose: async () => {},
      }
    },
    connectWorkerLanguageClient: async () => {
      workers.started++
      return {
        isStarted: () => true,
        getLanguageClient: () => {
          return {
            sendRequest: async (_method: string, params: { arguments: unknown[] }) => {
              workers.mappingRequests.push(params.arguments[0])
              return { oxsts: MAPPED_OXSTS, diagnostics: [] }
            },
            sendNotification: async () => {},
          }
        },
        dispose: async () => {},
      }
    },
  }
})

const { createLanguageRegistry } = await import('../../lib/language/registry')

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

  it('maps the model once its worker is up, and not before', async () => {
    let connect = (): void => {}
    workers.connecting = new Promise((resolve) => {
      connect = resolve
    })
    const socketsBefore = workers.sockets.length
    const mappingsBefore = workers.mappingRequests.length
    const unit = createLanguageRegistry(liveFlavors([])).unitOf('sysml')!
    const models = fakeModelDocuments()
    const runtime = unit.start(unit.flavors[0]!, {
      backendUrl: 'http://localhost:8080',
      documentUri: 'file:///workspace/snippet.sysml',
      colorMode: 'dark' as const,
      modelDocuments: models,
    })

    models.open('file:///workspace/snippet.sysml', 'part def Train;')
    await vi.waitFor(() => {
      expect(workers.sockets.length).toBe(socketsBefore + 1)
    })
    expect(workers.mappingRequests.length).toBe(mappingsBefore)
    expect(runtime.modelOxsts.get()).toBeNull()

    connect()
    await vi.waitFor(() => {
      expect(runtime.modelOxsts.get()?.text).toBe(MAPPED_OXSTS)
    })
    expect(workers.mappingRequests.slice(mappingsBefore)).toEqual(['file:///workspace/snippet.sysml'])
    workers.connecting = Promise.resolve()
    await runtime.dispose()
  })

  it('starts its worker once for the visit, however many runtimes it starts, and tells its session it is SysML v2', async () => {
    const registry = createLanguageRegistry(liveFlavors([]))
    const unit = registry.unitOf('sysml')!
    const flavor = unit.flavors[0]!
    const context = {
      backendUrl: 'http://localhost:8080',
      documentUri: 'file:///workspace/snippet.sysml',
      colorMode: 'dark' as const,
      modelDocuments: fakeModelDocuments(),
    }

    const before = workers.started
    const first = unit.start(flavor, context)
    await vi.waitFor(() => {
      expect(first.connection.get().status).toBe('connected')
    })
    await first.dispose()
    const second = unit.start(flavor, context)
    await vi.waitFor(() => {
      expect(second.connection.get().status).toBe('connected')
    })

    expect(workers.started).toBe(before + 1)
    expect(workers.sockets.at(-1)).toBe('oxsts-with-sysmlv2-library sysml')
    expect(second.initialization.get().steps.find((step) => {
      return step.id === 'server'
    })?.state).toBe('done')
    await second.dispose()
  })
})
