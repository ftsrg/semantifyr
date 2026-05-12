/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import { encodeCompressedBase64Url } from '../api/urls'

/**
 * Inputs required to build a shareable session URL: which flavor and example the user picked,
 * plus the current editor source. The example id is optional because "Open in new tab" on the
 * generated-OXSTS pane carries source without a registered example.
 *
 * <p>If {@code exampleCode} is provided AND matches {@code code} byte-for-byte, the {@code code}
 * parameter is omitted - the loader will start the recipient on the same example, which is both
 * shorter and faithful to "this is the example, unmodified".
 */
export interface ShareablePayload {
  flavorId: string
  exampleId?: string | undefined
  code: string
  /** The bundled example source. Used to suppress the {@code code} param when unchanged. */
  exampleCode?: string | undefined
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
  // Only include the code payload when it actually differs from the bundled example. The
  // loader pulls the bundled example by default; embedding it on every Copy link bloats the
  // URL with bytes that get overridden anyway.
  if (payload.exampleCode === undefined || payload.code !== payload.exampleCode) {
    params.set('code', await encodeCompressedBase64Url(payload.code))
  }
  return `${originAndPath}?${params.toString()}`
}
