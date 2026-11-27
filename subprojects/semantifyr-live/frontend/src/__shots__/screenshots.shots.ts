/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import { mkdirSync } from 'node:fs'
import { join, resolve } from 'node:path'
import type { ReportCall, ReportState, ReportTrace, ReportValue } from '@semantifyr/editor-common'
import type { Locator, Page } from 'playwright'
import { describe, expect } from 'vitest'

import { reportFor, statedCase, type StatedCase } from '../__ui__/fake/defaults'
import { casesListed, results, statesOf, test } from '../__ui__/harness'

// the pages show these, taken from live's own build against the interface tests' fake backend
const SCREENSHOTS = resolve(import.meta.dirname, '../../build/screenshots')

const THEMES = [
  'light',
  'dark',
] as const

const BARRIER = 'semantifyr::sysml::model::BarrierDownWhileTrainInCrossing'
const TWO_TRAINS = 'semantifyr::sysml::model::TwoTrainsCanBeInTheCrossing'

const STATES = 'semantifyr::sysml::states'

// in the order the report lists their variables
const PARTS = [
  { part: 'trackA', incoming: ['sensors'], outgoing: ['controller'] },
  { part: 'trackB', incoming: ['sensors'], outgoing: ['controller'] },
  { part: 'controller', incoming: ['trackA', 'trackB'], outgoing: ['barrier'] },
  { part: 'barrier', incoming: ['controller'], outgoing: [] },
]

function itemOf(name: string | undefined): string | null {
  return name === undefined ? null : `semantifyr::sysml::model::global_${name}`
}

function crossing(active: Readonly<Record<string, string>>, items: Readonly<Record<string, string>> = {}): ReportState {
  return {
    values: Object.fromEntries(PARTS.flatMap(({ part, incoming, outgoing }): [string, ReportValue][] => {
      return [
        [`root.system.${part}.Behaviour.activeState`, `root.system.${part}.Behaviour.${active[part]}`],
        ...incoming.flatMap((port): [string, ReportValue][] => {
          return [
            [`root.system.${part}.${port}.incomingItem`, itemOf(items[`${part}.${port}`])],
            [`root.system.${part}.${port}.consumed`, false],
          ]
        }),
        ...outgoing.map((port): [string, ReportValue] => {
          return [`root.system.${part}.${port}.outgoingItem`, itemOf(items[`${part}.${port}`])]
        }),
      ]
    })),
  }
}

function enter(part: string, state: string): ReportCall {
  return { self: `root.system.${part}.Behaviour.${state}`, transition: `${STATES}::State::enter`, arguments: { commonState: `root.system.${part}.Behaviour` }, calls: [] }
}

function exit(part: string, state: string): ReportCall {
  return { self: `root.system.${part}.Behaviour.${state}`, transition: `${STATES}::State::exit`, arguments: { commonState: `root.system.${part}.Behaviour` }, calls: [] }
}

function send(part: string, transition: string): ReportCall {
  return { self: `root.system.${part}.Behaviour.${transition}.action.send1`, transition: 'semantifyr::sysml::actions::SendItemAction::send', arguments: {}, calls: [] }
}

function fire(part: string, transition: string, calls: ReportCall[]): ReportCall {
  return { self: `root.system.${part}.Behaviour.${transition}`, transition: `${STATES}::Transition::fire`, arguments: {}, calls }
}

const COMMON_STATE = [
  { parameter: 'commonState', type: { kind: 'class' as const, name: `${STATES}::StateNode` } },
]

const BARRIER_TRACE: ReportTrace = {
  transitions: [
    { transition: `${STATES}::State::enter`, parameters: COMMON_STATE },
    { transition: `${STATES}::Transition::fire`, parameters: [] },
    { transition: `${STATES}::State::exit`, parameters: COMMON_STATE },
    { transition: 'semantifyr::sysml::actions::SendItemAction::send', parameters: [] },
  ],
  variables: PARTS.flatMap(({ part, incoming, outgoing }) => {
    return [
      { variable: `root.system.${part}.Behaviour.activeState`, type: { kind: 'feature' as const, name: `${STATES}::AbstractState::states` }, slot: null },
      ...incoming.flatMap((port) => {
        return [
          { variable: `root.system.${part}.${port}.incomingItem`, type: { kind: 'class' as const, name: 'semantifyr::sysml::items::Item' }, slot: null },
          { variable: `root.system.${part}.${port}.consumed`, type: { kind: 'datatype' as const, name: 'semantifyr::bool' }, slot: null },
        ]
      }),
      ...outgoing.map((port) => {
        return { variable: `root.system.${part}.${port}.outgoingItem`, type: { kind: 'class' as const, name: 'semantifyr::sysml::items::Item' }, slot: null }
      }),
    ]
  }),
  initial: {
    state: 0,
    calls: [
      enter('trackA', 'Free'),
      enter('trackB', 'Free'),
      enter('controller', 'Idle'),
      enter('barrier', 'Up'),
    ],
  },
  states: [
    crossing({ trackA: 'Free', trackB: 'Free', controller: 'Idle', barrier: 'Up' }),
    crossing({ trackA: 'Free', trackB: 'Approaching', controller: 'Idle', barrier: 'Up' }, { 'trackB.controller': 'TrainApproaching', 'controller.trackB': 'TrainApproaching' }),
    crossing({ trackA: 'Free', trackB: 'Near', controller: 'TrainPresent', barrier: 'Up' }, { 'controller.barrier': 'Lower', 'barrier.controller': 'Lower' }),
    crossing({ trackA: 'Free', trackB: 'InCrossing', controller: 'TrainPresent', barrier: 'Down' }),
    crossing({ trackA: 'Approaching', trackB: 'Free', controller: 'TrainPresent', barrier: 'Down' }, { 'trackA.controller': 'TrainApproaching', 'trackB.controller': 'TrainLeft', 'controller.trackA': 'TrainApproaching', 'controller.trackB': 'TrainLeft' }),
    crossing({ trackA: 'Near', trackB: 'Free', controller: 'Idle', barrier: 'Down' }, { 'controller.barrier': 'Lift', 'barrier.controller': 'Lift' }),
    crossing({ trackA: 'InCrossing', trackB: 'Free', controller: 'Idle', barrier: 'Up' }),
  ],
  steps: [
    [
      fire('trackB', 'transition1', [exit('trackB', 'Free'), send('trackB', 'transition1'), enter('trackB', 'Approaching')]),
    ],
    [
      fire('trackB', 'transition2', [exit('trackB', 'Approaching'), enter('trackB', 'Near')]),
      fire('controller', 'transition2', [exit('controller', 'Idle'), send('controller', 'transition2'), enter('controller', 'TrainPresent')]),
    ],
    [
      fire('trackB', 'transition3', [exit('trackB', 'Near'), enter('trackB', 'InCrossing')]),
      fire('barrier', 'transition1', [exit('barrier', 'Up'), enter('barrier', 'Down')]),
    ],
    [
      fire('trackA', 'transition1', [exit('trackA', 'Free'), send('trackA', 'transition1'), enter('trackA', 'Approaching')]),
      fire('trackB', 'transition4', [exit('trackB', 'InCrossing'), send('trackB', 'transition4'), enter('trackB', 'Free')]),
    ],
    [
      fire('trackA', 'transition2', [exit('trackA', 'Approaching'), enter('trackA', 'Near')]),
      fire('controller', 'transition4', [exit('controller', 'TrainPresent'), send('controller', 'transition4'), enter('controller', 'Idle')]),
    ],
    [
      fire('trackA', 'transition3', [exit('trackA', 'Near'), enter('trackA', 'InCrossing')]),
      fire('barrier', 'transition2', [exit('barrier', 'Down'), enter('barrier', 'Up')]),
    ],
  ].map((calls, index) => {
    return { source: index, target: index + 1, elapsed: 0, calls }
  }),
}

// the trace of the case that holds is not in the picture
const REACHED: ReportTrace = {
  transitions: [],
  variables: [],
  initial: { state: 0, calls: [] },
  states: [{ values: {} }],
  steps: [],
}

const CROSSING_CASES: readonly StatedCase[] = [
  statedCase(
    BARRIER,
    0,
    reportFor(BARRIER, { verdict: 'Failed', backendId: 'uppaal:default', seconds: 0.913567366, soundness: 'UnderApproximating', trace: BARRIER_TRACE }),
    reportFor(BARRIER, { verdict: 'Passed', backendId: 'uppaal:default', seconds: 5.288132211, soundness: 'UnderApproximating', kind: 're-check' }),
  ),
  statedCase(
    TWO_TRAINS,
    1,
    reportFor(TWO_TRAINS, { verdict: 'Passed', backendId: 'uppaal:default', seconds: 0.884851898, soundness: 'UnderApproximating', trace: REACHED }),
    reportFor(TWO_TRAINS, { verdict: 'Passed', backendId: 'uppaal:default', seconds: 1.296677753, soundness: 'UnderApproximating', kind: 're-check' }),
  ),
]

// every case has its verdict and its witness is validated, so nothing in the picture is still moving
async function verdictsShown(page: Page, rows: Locator): Promise<void> {
  await expect.poll(async () => {
    const states = await statesOf(rows, 'status-')
    return states.length > 0 && states.every((state) => {
      return state === 'passed' || state === 'failed'
    })
  }).toBe(true)
  await expect.poll(async () => {
    return page.locator('.validation-mark.is-validating, .validation-mark.is-validation-queued').count()
  }).toBe(0)
  await page.evaluate(async () => {
    await document.fonts.ready
  })
}

async function shoot(page: Page, name: string): Promise<void> {
  mkdirSync(SCREENSHOTS, { recursive: true })
  await page.mouse.move(0, 0)
  await page.screenshot({ path: join(SCREENSHOTS, `${name}.png`), animations: 'disabled', caret: 'hide' })
}

describe('the screenshots', () => {
  for (const theme of THEMES) {
    test(`live on the SysML v2 level crossing, verified with its trace open, ${theme}`, async ({ ui }) => {
      ui.backend.cases = CROSSING_CASES
      const page = await ui.open('/sysml/level-crossing', { width: 1280, height: 800 })
      await page.emulateMedia({ colorScheme: theme })
      await casesListed(page)
      await results(page).verifyAll.click()
      await verdictsShown(page, results(page).rows)
      await expect.poll(async () => {
        return page.locator('section.trace .case-trace .trace-frame .trace-editor:not(.is-loading)').count()
      }).toBe(1)

      await shoot(page, `live-${theme}`)
    })
  }
})
