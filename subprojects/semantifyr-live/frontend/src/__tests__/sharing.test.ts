/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import { describe, expect, it } from 'vitest'
import { buildShareableUrl } from '../lib/sharing'
import { decodeMaybeCompressedBase64Url } from '../lib/urls'

describe('buildShareableUrl', () => {
  it('embeds the flavor, example, and code as a decodable code parameter', async () => {
    const url = await buildShareableUrl('https://example.test/', {
      flavorId: 'oxsts',
      exampleId: 'trafficlight-direct-snapshot',
      code: 'package demo\nclass Foo {}',
    })
    const parsed = new URL(url)
    expect(parsed.searchParams.get('mode')).toBe('oxsts')
    expect(parsed.searchParams.get('example')).toBe('trafficlight-direct-snapshot')
    const code = parsed.searchParams.get('code')
    expect(code).not.toBeNull()
    const decoded = await decodeMaybeCompressedBase64Url(code!)
    expect(decoded).toBe('package demo\nclass Foo {}')
  })

  it('omits the example parameter when no example id is given', async () => {
    const url = await buildShareableUrl('https://example.test/', {
      flavorId: 'oxsts-with-gamma-library',
      code: 'package generated\nclass Bar {}',
    })
    const parsed = new URL(url)
    expect(parsed.searchParams.has('example')).toBe(false)
    expect(parsed.searchParams.get('mode')).toBe('oxsts-with-gamma-library')
  })
})
