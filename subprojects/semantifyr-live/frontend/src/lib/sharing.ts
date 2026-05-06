/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import { encodeCompressedBase64Url } from './urls'

/**
 * Inputs required to build a shareable session URL: which flavor and example the user picked,
 * plus the current editor source. The example id is optional because "Open in new tab" on the
 * generated-OXSTS pane carries source without a registered example.
 */
export interface ShareablePayload {
  flavorId: string
  exampleId?: string | undefined
  code: string
}

/** Builds a `?mode=...&example=...&code=...` URL anchored at {@code originAndPath}. */
export async function buildShareableUrl(
  originAndPath: string,
  payload: ShareablePayload,
): Promise<string> {
  const params = new URLSearchParams()
  params.set('mode', payload.flavorId)
  if (payload.exampleId) {
    params.set('example', payload.exampleId)
  }
  params.set('code', await encodeCompressedBase64Url(payload.code))
  return `${originAndPath}?${params.toString()}`
}
