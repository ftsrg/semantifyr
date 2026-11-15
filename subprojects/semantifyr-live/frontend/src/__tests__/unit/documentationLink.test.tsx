/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'

import { DocumentationLink } from '../../shells/desktop/Dialogs'
import { WEBSITE_URL } from '../../texts'

// Goal: the Documentation control is a link that opens the website's start page in a new tab
describe('DocumentationLink', () => {
  it('opens the start page in a new tab', () => {
    render(<DocumentationLink />)
    const link = screen.getByRole('link', { name: 'Documentation' })
    expect(link.getAttribute('href')).toBe(`${WEBSITE_URL}/learn/`)
    expect(link.getAttribute('target')).toBe('_blank')
    expect(link.getAttribute('rel')).toBe('noreferrer')
  })
})
