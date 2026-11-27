/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import { createRoot } from 'react-dom/client'
import { describe, expect, it, onTestFinished } from 'vitest'
import { page, server, userEvent } from 'vitest/browser'

import type { VerificationState } from '../lib/verification/state'
import { ShellRoot } from '../shells/ShellRoot'
import { caseOf, finished, seedLive, type Seed } from './seededLive'

import '@semantifyr/editor-common/tokens.css'
import '@semantifyr/editor-common/marks.css'
import '../ui/components.css'
import '../ui/base.css'

// Goal: the margin shows the marks of the shown document's cases, one state each; the documents menu holds a long list in a scroller; a discovery that failed says why

const BARRIER = caseOf('BarrierDownWhileTrainInCrossing', 4)
const TWO_TRAINS = caseOf('TwoTrainsCanBeInTheCrossing', 8)

function text(selector: string): string {
  return (document.querySelector<HTMLElement>(selector)?.innerText ?? '').replace(/\u00a0/g, ' ')
}

function marks(): string[] {
  return [...document.querySelectorAll<HTMLElement>('section.editor .verdict-glyph')].map((mark) => {
    return [...mark.classList].filter((name) => {
      return name.startsWith('verdict-glyph-')
    }).sort().join(' ')
  }).sort()
}

async function mount(seed: Seed): Promise<void> {
  const { live, shell } = seedLive(seed)
  const container = document.createElement('div')
  document.body.append(container)
  const root = createRoot(container)
  root.render(<ShellRoot live={live} shell={shell} colorMode="light" />)
  await expect.poll(() => {
    return document.querySelector('section.editor .monaco-editor .view-line')
  }, { timeout: 60_000 }).toBeTruthy()
  onTestFinished(() => {
    root.unmount()
    container.remove()
  })
}

function discovered(state: VerificationState['cases']): VerificationState {
  return { phase: 'done', discovery: 'discovered', cases: state }
}

describe('the read-only documents and the margin', () => {
  it('shows the marks of the cases of the document shown, one state each', async () => {
    await mount({
      verification: discovered([finished(BARRIER, 'failed', 2.9), { caseInfo: TWO_TRAINS, status: 'stale' }]),
    })

    await expect.poll(marks, { timeout: 30_000 }).toEqual(['verdict-glyph-failed', 'verdict-glyph-not-run'])
    await page.screenshot({ path: `../../build/screens/${server.browser}/readonly-marks-light.png` })
  })

  it('lists the SysML library files in a menu that scrolls', async () => {
    await mount({})
    await userEvent.click(document.querySelector<HTMLElement>('[aria-label="Read-only documents"]')!)
    await expect.poll(() => {
      return document.querySelector('.documents-filter')
    }).toBeTruthy()

    expect(text('.documents-button')).toContain('Documents')
    const list = document.querySelector<HTMLElement>('.documents-list')!
    expect(list.scrollHeight, 'the list scrolls inside the menu').toBeGreaterThan(list.clientHeight)
    await page.screenshot({ path: `../../build/screens/${server.browser}/readonly-documents-light.png` })
  })

  it('says why the cases are not listed when the discovery failed', async () => {
    await mount({ initializingAt: 'cases', initializationFailure: 'The verification cases could not be listed. The server did not list the verification cases within 30 s.' })

    await expect.poll(() => {
      return text('.initialization-note')
    }).toBe('The verification cases could not be listed. The server did not list the verification cases within 30 s.')
    expect(document.querySelector('.initialization-step.is-failed')?.textContent).toContain('Finding the verification cases')
    expect(text('.statusbar')).toContain('The verification cases could not be listed.')
    await page.screenshot({ path: `../../build/screens/${server.browser}/readonly-discovery-failed-light.png` })
  })
})
