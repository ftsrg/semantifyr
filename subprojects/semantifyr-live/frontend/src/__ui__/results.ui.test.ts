/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import type { VerificationCaseResult, VerificationCaseSpecification } from '@semantifyr/editor-common'
import type { Page } from 'playwright'
import { describe, expect } from 'vitest'

import { casesListed, editor, results, statesOf, test, type Ui } from './harness'
import type { CaseRequest } from './mock/session'

const LEVEL_CROSSING = '/oxsts/level-crossing?library=gamma'
const BARRIER = 'BarrierDownWhileTrainInCrossing'
const TWO_TRAINS = 'TwoTrainsCanBeInTheCrossing'

interface Gate {
  passed: Promise<void>
  open: () => void
}

function gate(): Gate {
  let open = (): void => {}
  const passed = new Promise<void>((resolve) => {
    open = resolve
  })
  return { passed, open }
}

// the verifications of the cases, each waiting for its case's gate or ending as the client cancels it
class Held {
  private readonly gates = new Map<string, Gate>()
  readonly started: string[] = []

  gateOf(name: string): Gate {
    let caseGate = this.gates.get(name)
    if (!caseGate) {
      caseGate = gate()
      this.gates.set(name, caseGate)
    }
    return caseGate
  }

  open(...names: string[]): void {
    for (const name of names) {
      this.gateOf(name).open()
    }
  }

  readonly verify = async (request: CaseRequest, recorded: () => VerificationCaseResult): Promise<VerificationCaseResult> => {
    const name = request.caseId?.split('::').at(-1) ?? ''
    this.started.push(name)
    await Promise.race([
      this.gateOf(name).passed,
      new Promise<void>((resolve) => {
        request.signal.addEventListener('abort', () => {
          resolve()
        })
      }),
    ])
    return recorded()
  }
}

async function rowStates(page: Page): Promise<string[]> {
  return statesOf(results(page).rows, 'status-')
}

async function opened(ui: Ui, address = LEVEL_CROSSING): Promise<Page> {
  const page = await ui.open(address, { height: 2000 })
  await casesListed(page)
  return page
}

describe('the rows', () => {
  test('go from not run through running and queued to their verdicts, and the counts follow', async ({ ui }) => {
    const held = new Held()
    ui.backend.answer('verify', held.verify)
    const page = await opened(ui)
    expect(await rowStates(page)).toEqual(['not-run', 'not-run'])

    await results(page).verifyAll.click()
    await expect.poll(async () => {
      return (await rowStates(page)).every((state) => {
        return state === 'running' || state === 'queued'
      })
    }).toBe(true)
    held.open(BARRIER, TWO_TRAINS)

    await expect.poll(async () => {
      return rowStates(page)
    }).toEqual(['failed', 'passed'])
    await expect.poll(async () => {
      return page.locator('.results-header .count').evaluateAll((counts: Element[]) => {
        return counts.map((count) => {
          return count.getAttribute('aria-label')
        })
      })
    }).toEqual(expect.arrayContaining(['Passed: 1', 'Failed: 1', 'Inconclusive: 0', 'Error: 0']))
  })

  test('are filtered by a count and shown again by Show all', async ({ ui }) => {
    const page = await opened(ui)
    await results(page).verifyAll.click()
    await expect.poll(async () => {
      return rowStates(page)
    }).toEqual(['failed', 'passed'])

    await page.locator('.results-header .count', { hasText: '1' }).and(page.getByRole('button', { name: 'Failed: 1' })).click()

    expect(await rowStates(page)).toEqual(['failed'])
    await expect.poll(async () => {
      return page.locator('.results-notice.is-filter').innerText()
    }).toContain('Showing 1 of 2')
    await page.getByRole('button', { name: 'Show all' }).click()
    expect(await rowStates(page)).toEqual(['failed', 'passed'])
  })

  test('show Inconclusive for a timeout and an unsupported construct, and Error for a broken run, the reason in the message', async ({ ui }) => {
    ui.backend.answer('verify', (request, recorded) => {
      const name = request.caseId?.split('::').at(-1)
      return name === BARRIER
        ? { ...recorded(), status: 'timeout', message: 'The portfolio timed out after 60 s', trace: null }
        : { ...recorded(), status: 'errored', message: 'The backend crashed', trace: null }
    })
    const page = await opened(ui)

    await results(page).verifyAll.click()

    await expect.poll(async () => {
      return rowStates(page)
    }).toEqual(['inconclusive', 'error'])
    expect(await results(page).row(BARRIER).locator('.case-message').innerText()).toContain('timed out')
    expect(await results(page).row(TWO_TRAINS).locator('.case-message').innerText()).toContain('The backend crashed')
  })
})

describe('a single run', () => {
  test('runs the case of its row alone and selects it', async ({ ui }) => {
    const page = await opened(ui)

    await results(page).row(TWO_TRAINS).getByRole('button', { name: `Run ${TWO_TRAINS}` }).click()

    await expect.poll(async () => {
      return rowStates(page)
    }).toEqual(['not-run', 'passed'])
    expect(ui.backend.session().commands('oxsts.case.verify')).toHaveLength(1)
    await expect.poll(async () => {
      return results(page).row(TWO_TRAINS).getAttribute('aria-selected')
    }).toBe('true')
  })

  test('runs a case not run from its margin mark, and a mark of a result selects its case', async ({ ui }) => {
    const page = await ui.open(LEVEL_CROSSING, { height: 5200 })
    await casesListed(page)
    const { marks } = editor(page)
    await expect.poll(async () => {
      return marks.count()
    }).toBe(2)

    await marks.first().click()

    await expect.poll(async () => {
      return statesOf(marks, 'verdict-glyph-')
    }).toEqual(['failed', 'not-run'])
    expect(ui.backend.session().commands('oxsts.case.verify')).toHaveLength(1)
    await results(page).row(TWO_TRAINS).click()
    await marks.first().click()
    await expect.poll(async () => {
      return results(page).row(BARRIER).getAttribute('aria-selected')
    }).toBe('true')
    expect(ui.backend.session().commands('oxsts.case.verify')).toHaveLength(1)
  })
})

describe('cancelling', () => {
  test('one case from its row lets the rest of the run go on', async ({ ui }) => {
    const held = new Held()
    ui.backend.answer('verify', held.verify)
    const page = await opened(ui)
    await results(page).verifyAll.click()
    await expect.poll(async () => {
      return held.started
    }).toEqual([BARRIER])

    await results(page).row(BARRIER).getByRole('button', { name: `Cancel ${BARRIER}` }).click()
    await expect.poll(async () => {
      return results(page).row(BARRIER).locator('.case-message').innerText()
    }).toContain('cancelled')
    held.open(TWO_TRAINS)

    await expect.poll(async () => {
      return rowStates(page)
    }).toEqual(['not-run', 'passed'])
  })

  test('all with the Cancel button ends the run with nothing verified', async ({ ui }) => {
    ui.backend.answer('verify', new Held().verify)
    const page = await opened(ui)
    await results(page).verifyAll.click()

    await results(page).cancel.click()

    await expect.poll(async () => {
      return rowStates(page)
    }).toEqual(['not-run', 'not-run'])
    await expect.poll(async () => {
      return results(page).verifyAll.isVisible()
    }).toBe(true)
  })
})

describe('a rerun', () => {
  test('keeps the verdicts until the new ones arrive, and shows the new one', async ({ ui }) => {
    const page = await opened(ui)
    await results(page).verifyAll.click()
    await expect.poll(async () => {
      return rowStates(page)
    }).toEqual(['failed', 'passed'])
    const held = new Held()
    ui.backend.answer('verify', async (request, recorded) => {
      const result = await held.verify(request, recorded)
      return request.caseId?.endsWith(BARRIER) ? { ...result, status: 'passed', trace: null } : result
    })

    await results(page).verifyAll.click()

    await expect.poll(async () => {
      return results(page).row(BARRIER).locator('.verdict-word').innerText()
    }).toBe('Failed')
    expect(await results(page).row(BARRIER).locator('.rerun-tag').count()).toBe(1)
    held.open(BARRIER, TWO_TRAINS)
    await expect.poll(async () => {
      return rowStates(page)
    }).toEqual(['passed', 'passed'])
  })
})

describe('the case list', () => {
  test('marks the results outdated after an edit and keeps them', async ({ ui }) => {
    const page = await opened(ui)
    await results(page).verifyAll.click()
    await expect.poll(async () => {
      return rowStates(page)
    }).toEqual(['failed', 'passed'])

    await editor(page).lines.click()
    await page.keyboard.press('Control+End')
    await page.keyboard.type('\n')

    await expect.poll(async () => {
      return results(page).column.locator('.case-row.is-outdated').count()
    }).toBe(2)
    expect(await rowStates(page)).toEqual(['failed', 'passed'])
    await expect.poll(async () => {
      return page.locator('section.editor .verdict-glyph-outdated').count()
    }).toBe(2)
  })

  test('follows the model as it is edited: a new case is not run and a removed one goes with its result', async ({ ui }) => {
    const page = await opened(ui)
    await results(page).verifyAll.click()
    await expect.poll(async () => {
      return rowStates(page)
    }).toEqual(['failed', 'passed'])
    let edited: (cases: VerificationCaseSpecification[]) => VerificationCaseSpecification[] = (cases) => {
      return cases
    }
    ui.backend.answer('discover', async (_request, recorded) => {
      return edited(recorded())
    })
    edited = (cases) => {
      const [barrier] = cases
      return [{ ...barrier!, id: `${barrier!.id}Renamed`, label: `${barrier!.label}Renamed` }, ...cases.slice(1)]
    }

    await editor(page).lines.click()
    await page.keyboard.press('Control+End')
    await page.keyboard.type('\n')

    await expect.poll(async () => {
      return results(page).rows.locator('.case-name').allInnerTexts()
    }).toEqual([`${BARRIER}Renamed`, TWO_TRAINS])
    expect(await rowStates(page)).toEqual(['not-run', 'passed'])
  })
})

describe('the selection after a run', () => {
  test('opens the first failed case with a trace after Verify all', async ({ ui }) => {
    const page = await opened(ui)

    await results(page).verifyAll.click()

    await expect.poll(async () => {
      return results(page).trace.locator('.trace-title .case-name').innerText()
    }).toContain(BARRIER)
    expect(await results(page).row(BARRIER).getAttribute('aria-selected')).toBe('true')
  })

  test('leaves a case the visitor selected during the run alone', async ({ ui }) => {
    const held = new Held()
    ui.backend.answer('verify', held.verify)
    const page = await opened(ui)
    await results(page).verifyAll.click()
    await expect.poll(async () => {
      return held.started
    }).toEqual([BARRIER])

    await results(page).row(TWO_TRAINS).click()
    held.open(BARRIER, TWO_TRAINS)

    await expect.poll(async () => {
      return rowStates(page)
    }).toEqual(['failed', 'passed'])
    await page.waitForTimeout(500)
    expect(await results(page).row(TWO_TRAINS).getAttribute('aria-selected')).toBe('true')
  })
})

describe('Verify all', () => {
  test('pressed during initialization starts once the cases are known', async ({ ui }) => {
    const discovery = gate()
    ui.backend.answer('discover', async (_request, recorded) => {
      await discovery.passed
      return recorded()
    })
    const page = await ui.open(LEVEL_CROSSING)
    await expect.poll(async () => {
      return page.locator('.results .initialization').count()
    }).toBe(1)

    await page.locator('.results-header').getByRole('button', { name: 'Verify all' }).click()
    await expect.poll(async () => {
      return page.locator('.initialization-pressed').innerText()
    }).toContain('Verify all is pressed')
    discovery.open()

    await expect.poll(async () => {
      return rowStates(page)
    }).toEqual(['failed', 'passed'])
  })

  test('a case run from its row while the language server initializes waits with its reason, and starts once the model\'s first diagnostics arrive', async ({ ui }) => {
    const diagnostics = gate()
    ui.backend.answer('diagnostics', async (_request, recorded) => {
      await diagnostics.passed
      return recorded()
    })
    const page = await opened(ui)
    const status = page.locator('[data-testid=status-message]')
    await expect.poll(async () => {
      return status.innerText()
    }).toBe('Initializing the OXSTS language server')

    await results(page).row(BARRIER).getByRole('button', { name: `Run ${BARRIER}` }).click()
    await expect.poll(async () => {
      return status.innerText()
    }).toBe(`Verify ${BARRIER} waits: initializing the OXSTS language server`)
    expect(await rowStates(page)).toEqual(['not-run', 'not-run'])
    diagnostics.open()

    await expect.poll(async () => {
      return rowStates(page)
    }).toEqual(['failed', 'not-run'])
  })

  test('does not run with errors in the model, and the status line says so', async ({ ui }) => {
    ui.backend.answer('diagnostics', () => {
      return [{ range: { start: { line: 0, character: 0 }, end: { line: 0, character: 7 } }, severity: 1, message: 'Something is wrong', source: 'semantifyr' }]
    })
    const page = await opened(ui)
    await expect.poll(async () => {
      return page.locator('.problem-counts').innerText()
    }).toContain('1')

    await results(page).verifyAll.click()

    await expect.poll(async () => {
      return results(page).status.innerText()
    }).toContain('Verify all did not run: the model has 1 error')
    expect(ui.backend.session().commands('oxsts.case.verify')).toEqual([])
  })

  test('pressed before the portfolios arrived runs with the portfolio the server names', async ({ ui }) => {
    const portfolios = gate()
    ui.backend.rest('GET /api/portfolios', async (_request, recorded) => {
      await portfolios.passed
      return recorded()
    })
    const page = await ui.open(LEVEL_CROSSING)
    await expect.poll(async () => {
      return page.locator('.results-header').getByRole('button', { name: /Verify all|Cancel/ }).count()
    }).toBe(1)

    await page.locator('.results-header').getByRole('button', { name: 'Verify all' }).click()
    expect(ui.backend.sessions).toEqual([])
    portfolios.open()

    await expect.poll(async () => {
      return rowStates(page)
    }).toEqual(['failed', 'passed'])
    expect(ui.backend.session().commands('oxsts.case.verify').map((argument) => {
      return (argument as { portfolio?: string }).portfolio
    })).toEqual(['smart-full', 'smart-full'])
  })
})

describe('the splitters of the desktop', () => {
  for (const [name, measure] of [
    ['Width of the results column', 'width'],
    ['Height of the case list', 'height'],
  ] as const) {
    test(`set the ${measure} a drag gives, and a double click returns it to its default`, async ({ ui }) => {
      const page = await opened(ui)
      const pane = measure === 'width' ? page.locator('.results') : page.getByTestId('case-list')
      const sizeOf = async (): Promise<number> => {
        return Math.round((await pane.boundingBox())?.[measure] ?? 0)
      }
      const initial = await sizeOf()
      const splitter = page.getByRole('separator', { name })
      const box = (await splitter.boundingBox())!
      const middle = { x: box.x + box.width / 2, y: box.y + box.height / 2 }

      await page.mouse.move(middle.x, middle.y)
      await page.mouse.down()
      await page.mouse.move(measure === 'width' ? middle.x - 150 : middle.x, measure === 'width' ? middle.y : middle.y + 150, { steps: 5 })
      await page.mouse.up()
      await expect.poll(async () => {
        return Math.abs(await sizeOf() - initial)
      }).toBeGreaterThan(100)
      await splitter.dblclick()

      await expect.poll(async () => {
        return sizeOf()
      }).toBe(initial)
    })
  }
})
