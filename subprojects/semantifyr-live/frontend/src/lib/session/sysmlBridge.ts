/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import {
  discoverVerificationCases,
  VerificationStatus,
  type FlavorInfo,
  type Location,
  type Range,
  type VerificationCaseResult,
  type VerificationCaseSpecification,
  type WitnessValidationResult,
} from '@semantifyr/editor-common'

import { SemantifyrLiveMethods, type ReadDocumentParams, type ReadDocumentResult, type VerificationsChangedParams } from '../api/lspExtensions'
import type { CancellationToken, LspClient } from '../verification/engine'
import type { ProblemEntry } from './problems'

export const SYSML_FLAVOR_ID = 'sysml'
export const SYSML_OXSTS_FLAVOR_ID = 'oxsts-with-sysmlv2-library'
export const SYSML_OXSTS_URI = 'file:///workspace/snippet.oxsts'
export const SYSML_HIDDEN_LANGUAGE_ID = 'sysml-mapped-oxsts'

const DISCOVER_COMMAND = 'sysml.case.discover'
const VERIFY_COMMAND = 'sysml.case.verify'
const VALIDATE_COMMAND = 'sysml.witness.validate'

export const SYSML_FLAVOR_INFO: FlavorInfo = {
  id: SYSML_FLAVOR_ID,
  displayName: 'SysML v2',
  description: 'A SysML v2 model, with the standard and verification libraries loaded in the background.',
  languageId: 'sysml',
  fileName: 'snippet.sysml',
  verificationCommand: VERIFY_COMMAND,
  discoveryCommand: DISCOVER_COMMAND,
  validateWitnessCommand: VALIDATE_COMMAND,
  validateWitnessByCase: false,
}

interface ServerDiagnostic {
  range: Range
  message: string
  severity?: number
  source?: string
}

export interface SysmlMapping {
  oxsts?: string
  diagnostics: ServerDiagnostic[]
}

interface VerificationCaseElement {
  name: string
  range: Range
}

interface WorkerCaseRequest {
  uri: string
  range: Range
  portfolio?: string
}

const SEVERITIES: readonly ProblemEntry['severity'][] = [
  'error',
  'warning',
  'info',
  'hint',
]

export function mapperProblems(mapping: SysmlMapping): ProblemEntry[] {
  return mapping.diagnostics
    .filter((diagnostic) => {
      return diagnostic.source === 'semantifyr'
    })
    .map((diagnostic) => {
      return {
        severity: SEVERITIES[(diagnostic.severity ?? 1) - 1] ?? 'error',
        startLine: diagnostic.range.start.line,
        startColumn: diagnostic.range.start.character,
        endLine: diagnostic.range.end.line,
        endColumn: diagnostic.range.end.character,
        message: diagnostic.message,
        source: 'semantifyr',
      }
    })
}

function errored(message: string): VerificationCaseResult {
  return {
    status: VerificationStatus.Errored,
    message,
    backendId: null,
    portfolioId: null,
    metrics: null,
    trace: null,
  }
}

function caseNameOf(specification: VerificationCaseSpecification): string {
  return specification.id.split('::').at(-1) ?? specification.id
}

function rangeKey(range: Range): string {
  return `${range.start.line}:${range.start.character}-${range.end.line}:${range.end.character}`
}

function executeCommand(
  client: LspClient,
  command: string,
  argument: unknown,
  token?: CancellationToken,
): Promise<unknown> {
  const params = { command, arguments: [argument] }
  return token === undefined
    ? client.sendRequest('workspace/executeCommand', params)
    : client.sendRequest('workspace/executeCommand', params, token)
}

export interface SysmlBridgeOptions {
  fileUri: string
  getWorkerClient: () => LspClient | undefined
  getOxstsClient: () => LspClient | undefined
}

export interface SysmlBridge {
  client: LspClient
  readMapping: () => Promise<SysmlMapping>
  // keeps the OXSTS of the model open in the session, where its read-only document navigates
  showOxsts: (text: string) => void
  translateNotification: (method: string, params: unknown) => unknown
  witnessUriOf: (traceUri: string) => string | undefined
}

export function createSysmlBridge(options: SysmlBridgeOptions): SysmlBridge {
  const traceModels = new Map<string, string>()
  const witnessUris = new Map<string, string>()
  const verifiedCaseLocations = new Map<string, Location>()

  let openedOn: LspClient | null = null
  let sentText = ''
  let version = 0

  const workerClient = (): LspClient => {
    const client = options.getWorkerClient()
    if (!client) {
      throw new Error('The SysML language server is not running')
    }
    return client
  }

  const oxstsClient = (): LspClient => {
    const client = options.getOxstsClient()
    if (!client) {
      throw new Error('The Semantifyr server is not connected')
    }
    return client
  }

  const readMapping = async (): Promise<SysmlMapping> => {
    return (await executeCommand(workerClient(), 'syside.semantifyr.sysml.oxsts', options.fileUri)) as SysmlMapping
  }

  const syncOxsts = (client: LspClient, text: string): void => {
    if (openedOn !== client) {
      openedOn = client
      sentText = text
      version = 1
      void Promise.resolve(client.sendNotification('textDocument/didOpen', {
        textDocument: { uri: SYSML_OXSTS_URI, languageId: 'oxsts', version, text },
      })).catch(() => {})
      return
    }
    if (text === sentText) {
      return
    }
    sentText = text
    version += 1
    void Promise.resolve(client.sendNotification('textDocument/didChange', {
      textDocument: { uri: SYSML_OXSTS_URI, version },
      contentChanges: [{ text }],
    })).catch(() => {})
  }

  const liftTrace = async (
    caseName: string,
    result: VerificationCaseResult,
  ): Promise<VerificationCaseResult> => {
    if (!result.trace) {
      return result
    }
    const traceUri = `file:///workspace/traces/${caseName}.trace.sysml`
    if (result.trace.witnessUri) {
      witnessUris.set(traceUri, result.trace.witnessUri)
    }
    traceModels.delete(traceUri)
    let message = result.message
    try {
      traceModels.set(
        traceUri,
        (await executeCommand(workerClient(), 'syside.semantifyr.sysml.trace', {
          uri: options.fileUri,
          caseName,
          trace: result.trace.callTrace,
        })) as string,
      )
    } catch (error) {
      const reason = error instanceof Error ? error.message : String(error)
      message = [message, `The SysML trace could not be generated: ${reason}`].filter(Boolean).join('\n')
    }
    return { ...result, message, trace: { ...result.trace, witnessUri: traceUri } }
  }

  const verify = async (request: WorkerCaseRequest, token?: CancellationToken): Promise<VerificationCaseResult> => {
    const mapping = await readMapping()
    if (mapping.oxsts === undefined) {
      return errored('The model has errors or constructs Semantifyr does not support, see the problems.')
    }
    const requested = (await discover()).find((specification) => {
      return rangeKey(specification.location.range) === rangeKey(request.range)
    })
    if (!requested) {
      return errored('The model has no verification case at the requested location')
    }
    const caseName = requested.label
    const client = oxstsClient()
    syncOxsts(client, mapping.oxsts)
    const discovered = await discoverVerificationCases(client, 'oxsts.case.discover', SYSML_OXSTS_URI)
    const target = discovered.find((specification) => {
      return caseNameOf(specification) === caseName
    })
    if (!target) {
      return errored(`The OXSTS of the model has no case named ${caseName}`)
    }
    verifiedCaseLocations.set(rangeKey(target.location.range), {
      uri: options.fileUri,
      range: request.range,
    })
    const result = (await executeCommand(
      client,
      'oxsts.case.verify',
      { ...request, uri: SYSML_OXSTS_URI, range: target.location.range },
      token,
    )) as VerificationCaseResult
    return liftTrace(caseName, result)
  }

  const validate = async (
    request: WorkerCaseRequest,
    token?: CancellationToken,
  ): Promise<WitnessValidationResult> => {
    const witnessUri = witnessUris.get(request.uri)
    if (!witnessUri) {
      return { status: 'errored', verdict: null, message: 'No witness to validate', backendId: null, portfolioId: null, metrics: null }
    }
    return (await executeCommand(
      oxstsClient(),
      'oxsts.case.validateWitness',
      { ...request, uri: witnessUri },
      token,
    )) as WitnessValidationResult
  }

  const discover = async (): Promise<VerificationCaseSpecification[]> => {
    const elements = (await executeCommand(
      workerClient(),
      'syside.semantifyr.sysml.verificationCases',
      options.fileUri,
    )) as VerificationCaseElement[]
    return elements.map((element) => {
      return {
        id: element.name,
        label: element.name,
        location: { uri: options.fileUri, range: element.range },
      }
    })
  }

  const traceUriOf = (witnessUri: string): string | undefined => {
    return [...witnessUris].find(([, candidate]) => {
      return candidate === witnessUri
    })?.[0]
  }

  // a validation names the OXSTS witness, which the page knows by the case's trace
  const translateLocation = (location: Location): Location | undefined => {
    if (location.uri === SYSML_OXSTS_URI) {
      return verifiedCaseLocations.get(rangeKey(location.range))
    }
    const traceUri = traceUriOf(location.uri)
    return traceUri === undefined ? undefined : { ...location, uri: traceUri }
  }

  const translateActive = (params: VerificationsChangedParams | null): VerificationsChangedParams => {
    return {
      active: params?.active?.map((item) => {
        const location = item.location ? translateLocation(item.location) : undefined
        return location ? { ...item, location } : item
      }) ?? [],
    }
  }

  const executeWorkspaceCommand = async (
    params: { command: string; arguments?: unknown[] },
    token?: CancellationToken,
  ): Promise<unknown> => {
    const argument = params.arguments?.[0]
    switch (params.command) {
      case DISCOVER_COMMAND:
        return discover()
      case VERIFY_COMMAND:
        return verify(argument as WorkerCaseRequest, token)
      case VALIDATE_COMMAND:
        return validate(argument as WorkerCaseRequest, token)
      default:
        return null
    }
  }

  const sendRequest = async (method: string, params?: unknown, token?: CancellationToken): Promise<unknown> => {
    switch (method) {
      case 'workspace/executeCommand':
        return executeWorkspaceCommand(params as { command: string; arguments?: unknown[] }, token)
      case SemantifyrLiveMethods.READ_DOCUMENT:
        return readDocument(params as ReadDocumentParams)
      case SemantifyrLiveMethods.LIST_VERIFICATIONS:
        return translateActive((await oxstsClient().sendRequest(method, params)) as VerificationsChangedParams | null)
      default:
        return oxstsClient().sendRequest(method, params)
    }
  }

  const readDocument = async ({ uri }: ReadDocumentParams): Promise<ReadDocumentResult | null> => {
    if (uri === SYSML_OXSTS_URI) {
      return { text: (await readMapping()).oxsts ?? null }
    }
    const traceModel = traceModels.get(uri)
    if (traceModel !== undefined) {
      return { text: traceModel }
    }
    if (witnessUris.has(uri)) {
      return { text: null }
    }
    return (await oxstsClient().sendRequest(SemantifyrLiveMethods.READ_DOCUMENT, { uri })) as ReadDocumentResult | null
  }

  return {
    client: {
      sendRequest,
      sendNotification: (method, params) => {
        return oxstsClient().sendNotification(method, params)
      },
    },
    readMapping,
    showOxsts: (text) => {
      const client = options.getOxstsClient()
      if (client) {
        syncOxsts(client, text)
      }
    },
    translateNotification: (method, params) => {
      return method === SemantifyrLiveMethods.VERIFICATIONS_CHANGED
        ? translateActive(params as VerificationsChangedParams | null)
        : params
    },
    witnessUriOf: (traceUri) => {
      return witnessUris.get(traceUri)
    },
  }
}
