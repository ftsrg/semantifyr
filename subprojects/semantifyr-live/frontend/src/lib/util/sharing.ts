/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import { encodeCompressedBase64Url } from '../api/urls'

export interface ShareablePayload {
  flavorId: string
  exampleId?: string | undefined
  code: string
  // When set and equal to `code`, the `?code=` param is omitted from the URL.
  exampleCode?: string | undefined
}

export async function buildShareableUrl(
  originAndPath: string,
  payload: ShareablePayload,
): Promise<string> {
  const params = new URLSearchParams()
  params.set('mode', payload.flavorId)
  if (payload.exampleId) {
    params.set('example', payload.exampleId)
  }
  if (payload.exampleCode === undefined || payload.code !== payload.exampleCode) {
    params.set('code', await encodeCompressedBase64Url(payload.code))
  }
  return `${originAndPath}?${params.toString()}`
}
