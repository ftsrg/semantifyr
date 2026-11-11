/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

export type MessageId = number | string

export interface RequestMessage {
  jsonrpc: '2.0'
  id: MessageId
  method: string
  params?: unknown
}

export interface NotificationMessage {
  jsonrpc: '2.0'
  method: string
  params?: unknown
}

export interface ResponseError {
  code: number
  message: string
}

export interface ResponseMessage {
  jsonrpc: '2.0'
  id: MessageId | null
  result?: unknown
  error?: ResponseError
}

export type Message =
  | RequestMessage
  | NotificationMessage
  | ResponseMessage

export const ErrorCodes = {
  MethodNotFound: -32601,
  InternalError: -32603,
  RequestCancelled: -32800,
} as const

export function isRequest(message: Message): message is RequestMessage {
  return 'method' in message && 'id' in message
}

export function isNotification(message: Message): message is NotificationMessage {
  return 'method' in message && !('id' in message)
}

export function isResponse(message: Message): message is ResponseMessage {
  return !('method' in message)
}

// the session socket carries one JSON-RPC message per WebSocket message, without the Content-Length header of a stream
export function parseMessage(frame: string | Buffer): Message {
  const text = typeof frame === 'string' ? frame : frame.toString('utf-8')
  const message = JSON.parse(text) as Partial<Message>
  if (message.jsonrpc !== '2.0') {
    throw new Error(`not a JSON-RPC 2.0 message: ${text.slice(0, 200)}`)
  }
  return message as Message
}

export function encodeMessage(message: Message): string {
  return JSON.stringify(message)
}

export function request(id: MessageId, method: string, params?: unknown): RequestMessage {
  return params === undefined ? { jsonrpc: '2.0', id, method } : { jsonrpc: '2.0', id, method, params }
}

export function notification(method: string, params?: unknown): NotificationMessage {
  return params === undefined ? { jsonrpc: '2.0', method } : { jsonrpc: '2.0', method, params }
}

export function response(id: MessageId, result: unknown): ResponseMessage {
  return { jsonrpc: '2.0', id, result }
}

export function errorResponse(id: MessageId, error: ResponseError): ResponseMessage {
  return { jsonrpc: '2.0', id, error }
}
