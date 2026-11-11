/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import { createRoot } from 'react-dom/client'
import { describe, expect, it, onTestFinished } from 'vitest'
import { cdp, page, userEvent } from 'vitest/browser'

import { connectionLost, INITIAL_CONNECTION } from '../lib/stores/connection'
import type { ResolvedColorMode } from '../lib/util/colorMode'
import type { VerificationCaseState, VerificationState } from '../lib/verification/engine'
import { documentKey } from '../lib/workspace/live'
import { ShellRoot } from '../shells/ShellRoot'
import { CaseTrace } from '../trace/CaseTrace'
import { caseOf, finished, seedLive, type Seed, type Seeded } from './seededLive'
import { caseState, LANGUAGES, seedWithTrace } from './traces'

// Goal: an embed in a frame of a fixed size fills it and never grows: the document does not scroll, the open trace and the model
// both show inside the frame, beside each other in a slide's frame and below each other in a narrow one

const GAMMA = LANGUAGES.gamma!
const GAMMA_URI = 'file:///workspace/snippet.gamma'
const BARRIER = caseOf('BarrierDownWhileTrainInCrossing', 87, GAMMA_URI)
const TWO_TRAINS = caseOf('TwoTrainsCanBeInTheCrossing', 94, GAMMA_URI)
const TRACED = caseState(GAMMA, { validated: true, recorded: true })

const EXCERPT = `package LevelCrossing

statechart Track [
    port sensors : requires Sensors
    port controller : provides TrackReports
] {
    transition from Init to Free
    transition from Free to Approaching when sensors.approach / raise controller.approaching
    region Main {
        initial Init
        state Free
        state Approaching
    }
}
`

function discovered(cases: VerificationCaseState[], phase: VerificationState['phase'] = 'idle'): VerificationState {
  return { phase, discovery: 'discovered', cases }
}

function gamma(seed: Seed): Seed {
  return { flavorId: GAMMA.flavorId, exampleId: GAMMA.exampleId, ...seed }
}

function traced(): Seeded {
  return seedWithTrace(GAMMA, TRACED, [finished(TWO_TRAINS, 'passed', 0.8)])
}

function justDone(): Seeded {
  const finishedAt = Date.now()
  return seedWithTrace(GAMMA, TRACED, [finished(TWO_TRAINS, 'passed', 0.8)], { timing: { key: documentKey(GAMMA.flavorId, GAMMA.exampleId, false), caseId: null, startedAt: finishedAt - 2700, finishedAt } })
}

function manyCases(): Seeded {
  const others = Array.from({ length: 9 }, (_, index) => {
    return finished(caseOf(`SafetyRequirement${index + 1}`, 94, GAMMA_URI), 'passed', 0.4)
  })
  return seedWithTrace(GAMMA, TRACED, others)
}

interface Frame {
  width: number
  height: number
  beside: boolean
}

const SLIDE: Frame = { width: 1137, height: 494, beside: true }
const ZOOMED_SLIDE: Frame = { width: 850, height: 590, beside: true }
const NARROW: Frame = { width: 420, height: 600, beside: false }

interface ScreenState {
  frame: Frame
  view: 'embed' | 'edit-only'
  seed: Seed | (() => Seeded)
  act?: () => Promise<void>
  expectState: () => void
}

// a real pointer, which the splitter captures, in the coordinates of the page that frames the test
async function mouse(type: 'mousePressed' | 'mouseMoved' | 'mouseReleased', x: number, y: number, clickCount = 1): Promise<void> {
  const offset = window.frameElement?.getBoundingClientRect() ?? { left: 0, top: 0 }
  await cdp().send('Input.dispatchMouseEvent', { type, x: offset.left + x, y: offset.top + y, button: 'left', buttons: type === 'mouseReleased' ? 0 : 1, clickCount })
}

async function drag(fromX: number, toX: number): Promise<void> {
  const y = window.innerHeight / 2
  await mouse('mousePressed', fromX, y)
  for (const step of [0.25, 0.5, 0.75, 1]) {
    await mouse('mouseMoved', fromX + (toX - fromX) * step, y)
  }
  await mouse('mouseReleased', toX, y)
}

function splitterX(): number {
  const splitter = box('.embed > .splitter')
  return splitter.left + splitter.width / 2
}

function columns(): { editor: number, results: number } {
  return { editor: Math.round(box('.embed-editor').width), results: Math.round(box('.embed-strip').width) }
}

const SPLITS: { editor: number, results: number }[] = []

function text(selector: string): string {
  return (document.querySelector<HTMLElement>(selector)?.innerText ?? '').replace(/\u00a0/g, ' ')
}

function count(selector: string): number {
  return document.querySelectorAll(selector).length
}

function box(selector: string): DOMRect {
  const element = document.querySelector(selector)
  expect(element, selector).toBeTruthy()
  return element!.getBoundingClientRect()
}

function expectInFrame(selector: string): void {
  const rectangle = box(selector)
  expect(rectangle.height, `${selector} shows`).toBeGreaterThan(0)
  expect(rectangle.top, `${selector} top`).toBeGreaterThanOrEqual(0)
  expect(rectangle.left, `${selector} left`).toBeGreaterThanOrEqual(0)
  expect(rectangle.bottom, `${selector} bottom`).toBeLessThanOrEqual(window.innerHeight + 0.5)
  expect(rectangle.right, `${selector} right`).toBeLessThanOrEqual(window.innerWidth + 0.5)
}

function expectWhole(selector: string): void {
  const element = document.querySelector<HTMLElement>(selector)!
  expect(element.scrollWidth, `${selector} cut`).toBeLessThanOrEqual(element.clientWidth + 1)
  expectInFrame(selector)
}

// at the strip's font a line is under 18 px
function expectStatusLines(most: number): void {
  expect(box('[data-testid=status-message] > span').height, 'the status lines').toBeLessThanOrEqual(most * 18)
}

function expectTraceAndModel(frame: Frame): void {
  expect(text('.embed-trace [data-testid=trace-title]')).toContain(TRACED.caseInfo.id)
  expect(document.querySelector('.embed-trace [data-testid=trace-title] .verdict-mark.status-failed'), 'the verdict in the trace title').toBeTruthy()
  expect(text('.embed-trace .trace-views [aria-pressed=true]')).toBe('Gamma trace')
  expect(text('.embed-trace [data-testid=run-details]')).toContain('decided by Theta')
  expectInFrame('.embed-trace [data-testid=trace-title]')
  expectInFrame('.embed-editor')
  const editor = box('.embed-editor')
  const trace = box('.embed-trace')
  if (frame.beside) {
    expectInFrame('.embed-trace .trace-frame')
    expect(trace.left, 'the trace beside the model').toBeGreaterThanOrEqual(editor.right - 1)
    expect(editor.height, 'the model takes the full height').toBeGreaterThan(window.innerHeight - 10)
  } else {
    expect(box('.embed-trace .trace-frame').top, 'the trace text starts in the frame').toBeLessThan(window.innerHeight - 60)
    expect(trace.top, 'the trace below the model').toBeGreaterThanOrEqual(editor.bottom - 1)
    expect(editor.height, 'the model keeps its minimum').toBeGreaterThanOrEqual(220)
  }
}

function framed(frame: Frame): Readonly<Record<string, ScreenState>> {
  return {
    rest: {
      frame,
      view: 'embed',
      seed: gamma({ verification: discovered([{ caseInfo: BARRIER, status: 'stale' }, { caseInfo: TWO_TRAINS, status: 'stale' }]) }),
      expectState: () => {
        expect(count('.strip-bar .button-primary')).toBe(1)
        expect(count('.embed-case.status-not-run')).toBe(2)
        expect(text('[data-testid=status-message]')).toContain('Ready: 2 verification cases')
        expectInFrame('.strip-bar')
        expectInFrame('.embed-case:last-child')
      },
    },
    running: {
      frame,
      view: 'embed',
      seed: gamma({ verification: discovered([{ caseInfo: BARRIER, status: 'running' }, { caseInfo: TWO_TRAINS, status: 'queued' }], 'running') }),
      expectState: () => {
        expect(count('.strip-bar .button-cancel')).toBe(1)
        expect(count('.embed-case.status-running')).toBe(1)
        expect(text('[data-testid=status-message]')).toContain('Verifying')
        expectInFrame('.strip-bar .button-cancel')
      },
    },
    trace: {
      frame,
      view: 'embed',
      seed: traced,
      expectState: () => {
        expect(count('.embed-case.is-open')).toBe(1)
        expect(count('.embed-case.status-passed')).toBe(1)
        expectTraceAndModel(frame)
      },
    },
  }
}

const STATES: Readonly<Record<string, ScreenState>> = {
  ...Object.fromEntries(Object.entries(framed(SLIDE)).map(([name, state]) => {
    return [`slide-${name}`, state]
  })),
  ...Object.fromEntries(Object.entries(framed(ZOOMED_SLIDE)).map(([name, state]) => {
    return [`zoomed-slide-${name}`, state]
  })),
  'slide-many-cases': {
    frame: SLIDE,
    view: 'embed',
    seed: manyCases,
    expectState: () => {
      expect(count('.embed-case')).toBe(10)
      const cases = document.querySelector('.embed-cases')!
      expect(cases.scrollHeight, 'the case lines scroll').toBeGreaterThan(cases.clientHeight)
      expectTraceAndModel(SLIDE)
    },
  },
  'zoomed-slide-reconnecting': {
    frame: ZOOMED_SLIDE,
    view: 'embed',
    seed: gamma({
      verification: discovered([finished(BARRIER, 'failed', 2.9), finished(TWO_TRAINS, 'passed', 0.8)], 'done'),
      connection: connectionLost(INITIAL_CONNECTION, 'connecting', 'Connection lost', { reconnecting: true, retryAt: Date.now() + 300_000 }),
    }),
    expectState: () => {
      expect(text('[data-testid=status-message]')).toMatch(/Connection lost\. Reconnecting in \d+ s\. Reconnect now/)
      expectWhole('[data-testid=status-message] .button-link')
      expectStatusLines(2)
    },
  },
  'slide-reconnecting': {
    frame: SLIDE,
    view: 'embed',
    seed: gamma({
      verification: discovered([finished(BARRIER, 'failed', 2.9), finished(TWO_TRAINS, 'passed', 0.8)], 'done'),
      connection: connectionLost(INITIAL_CONNECTION, 'connecting', 'Connection lost', { reconnecting: true, retryAt: Date.now() + 300_000 }),
    }),
    expectState: () => {
      expectWhole('[data-testid=status-message] .button-link')
      expectStatusLines(2)
    },
  },
  'zoomed-slide-done': {
    frame: ZOOMED_SLIDE,
    view: 'embed',
    seed: justDone,
    expectState: () => {
      expect(text('[data-testid=status-message]')).toMatch(/Done in 2\.7 s: 1 failed, 1 passed/)
      expectStatusLines(2)
      for (const caseInfo of [BARRIER, TWO_TRAINS]) {
        const name = `.embed-case[data-case="${caseInfo.id}"] .case-name`
        expect(text(name), 'the whole case name').toBe(caseInfo.id)
        expectWhole(name)
      }
      expectTraceAndModel(ZOOMED_SLIDE)
      const editor = box('.embed-trace .trace-editor')
      const spans = [...document.querySelectorAll('.embed-trace .trace-editor .view-line > span')]
      expect(spans.length).toBeGreaterThan(5)
      for (const span of spans) {
        expect(span.getBoundingClientRect().right, `the trace line "${(span as HTMLElement).innerText}" wraps`).toBeLessThanOrEqual(editor.right + 1)
      }
    },
  },
  'slide-split': {
    frame: SLIDE,
    view: 'embed',
    seed: traced,
    act: async () => {
      SPLITS.length = 0
      await drag(splitterX(), 700)
      SPLITS.push(columns())
      await drag(splitterX(), 50)
      SPLITS.push(columns())
      await drag(splitterX(), 1130)
      SPLITS.push(columns())
      const x = splitterX()
      const y = window.innerHeight / 2
      await mouse('mouseMoved', x, y)
      await mouse('mousePressed', x, y, 1)
      await mouse('mouseReleased', x, y, 1)
      await mouse('mousePressed', x, y, 2)
      await mouse('mouseReleased', x, y, 2)
      await new Promise((resolve) => {
        setTimeout(resolve, 100)
      })
      SPLITS.push(columns())
      document.querySelector<HTMLElement>('.embed > .splitter')!.focus()
      await userEvent.keyboard('{ArrowLeft}')
      await new Promise((resolve) => {
        setTimeout(resolve, 100)
      })
      SPLITS.push(columns())
    },
    expectState: () => {
      const [dragged, widest, narrowest, reset, keyed] = SPLITS
      expect(document.querySelector('.embed > .splitter')!.getAttribute('role')).toBe('separator')
      expect(dragged!.editor, 'the editor ends at the drop').toBeGreaterThanOrEqual(695)
      expect(dragged!.editor).toBeLessThanOrEqual(705)
      expect(widest!.editor, 'the editor keeps its minimum').toBeGreaterThanOrEqual(279)
      expect(widest!.editor).toBeLessThanOrEqual(282)
      expect(narrowest!.results, 'the results keep their minimum').toBeGreaterThanOrEqual(319)
      expect(narrowest!.results).toBeLessThanOrEqual(322)
      expect(Math.abs(reset!.editor - reset!.results), 'a double click restores the even split').toBeLessThanOrEqual(2)
      expect(keyed!.results - reset!.results, 'the left arrow widens the results').toBe(16)
      expectTraceAndModel(SLIDE)
    },
  },
  'narrow-trace': {
    frame: NARROW,
    view: 'embed',
    seed: traced,
    expectState: () => {
      expect(count('.embed-case.is-open')).toBe(1)
      expectTraceAndModel(NARROW)
    },
  },
  'slide-edit-only': {
    frame: SLIDE,
    view: 'edit-only',
    seed: gamma({ text: EXCERPT }),
    expectState: () => {
      expect(count('.embed-strip')).toBe(0)
      expect(text('.monaco-editor .view-lines')).toContain('statechart Track')
      expect(box('.embed-editor').height, 'the editor fills the frame, not its text').toBeGreaterThan(window.innerHeight - 10)
    },
  },
}

describe('the embed in a fixed frame', () => {
  for (const colorMode of ['light', 'dark'] as const satisfies readonly ResolvedColorMode[]) {
    for (const [state, screen] of Object.entries(STATES)) {
      it(`fills ${screen.frame.width} by ${screen.frame.height} with ${state} in the ${colorMode} theme`, async () => {
        await page.viewport(screen.frame.width, screen.frame.height)
        const address = `${window.location.pathname}${window.location.search}`
        window.history.replaceState(null, '', `${window.location.pathname}?view=${screen.view}&frame=fixed`)
        const { live, shell } = typeof screen.seed === 'function' ? screen.seed() : seedLive(screen.seed)
        const container = document.createElement('div')
        document.body.append(container)
        const root = createRoot(container)
        onTestFinished(() => {
          root.unmount()
          container.remove()
          window.history.replaceState(null, '', address)
        })
        root.render(<ShellRoot live={live} shell={shell} colorMode={colorMode} trace={CaseTrace} />)
        await expect.poll(() => {
          return document.querySelector('.monaco-editor .view-line')
        }, { timeout: 60_000 }).toBeTruthy()
        await screen.act?.()
        await new Promise((resolve) => {
          setTimeout(resolve, 600)
        })
        screen.expectState()

        const embed = document.querySelector<HTMLElement>('[data-testid=embed-shell]')!
        const frame = embed.getBoundingClientRect()
        expect(window.innerWidth).toBe(screen.frame.width)
        expect(window.innerHeight).toBe(screen.frame.height)
        expect(document.documentElement.scrollHeight, 'the document does not scroll').toBeLessThanOrEqual(window.innerHeight)
        expect(document.documentElement.scrollWidth, 'the document does not scroll sideways').toBeLessThanOrEqual(window.innerWidth)
        expect(Math.round(frame.height), 'the embed fills the frame').toBe(screen.frame.height)
        for (const selector of ['.strip-bar', '.embed-case-verdict', '.embed-case-end']) {
          for (const element of document.querySelectorAll(selector)) {
            expect(element.scrollWidth, `${selector} clipped`).toBeLessThanOrEqual(element.clientWidth + 1)
          }
        }
        await page.screenshot({ path: `../../build/screens/embed-fixed-${state}-${colorMode}.png`, element: embed })
      })
    }
  }
})
