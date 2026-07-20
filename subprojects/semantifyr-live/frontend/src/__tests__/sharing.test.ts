/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import { describe, expect, it } from 'vitest'
import { buildShareableUrl } from '../lib/util/sharing'
import { decodeCompressedBase64Url } from '../lib/api/urls'

describe('buildShareableUrl', () => {
  it('embeds the flavor, example, and code as a decodable code parameter', async () => {
    const url = await buildShareableUrl('https://example.test/', {
      flavorId: 'oxsts',
      exampleId: 'basics',
      code: 'package demo\nclass Foo {}',
    })
    const parsed = new URL(url)
    expect(parsed.searchParams.get('mode')).toBe('oxsts')
    expect(parsed.searchParams.get('example')).toBe('basics')
    const code = parsed.searchParams.get('code')
    expect(code).not.toBeNull()
    const decoded = await decodeCompressedBase64Url(code!)
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

  it('omits the code parameter when the editor still matches the bundled example', async () => {
    const exampleCode = 'package demo\nclass Foo {}'
    const url = await buildShareableUrl('https://example.test/', {
      flavorId: 'oxsts',
      exampleId: 'sample',
      code: exampleCode,
      exampleCode,
    })
    const parsed = new URL(url)
    expect(parsed.searchParams.has('code')).toBe(false)
    expect(parsed.searchParams.get('example')).toBe('sample')
  })

  it('keeps the code parameter when the editor diverges from the bundled example', async () => {
    const url = await buildShareableUrl('https://example.test/', {
      flavorId: 'oxsts',
      exampleId: 'sample',
      code: 'package demo\nclass Foo { var x: int := 1 }',
      exampleCode: 'package demo\nclass Foo {}',
    })
    const parsed = new URL(url)
    expect(parsed.searchParams.has('code')).toBe(true)
  })
})
