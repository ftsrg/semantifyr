/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import { readFileSync } from 'node:fs'
import { join } from 'node:path'
import type { Page } from 'playwright'
import { describe, expect } from 'vitest'

import { editorKeysOf, TEST_BROWSER } from '../testBrowser'
import { casesListed, editor, editorWorkerLoaded, results, statesOf, test } from './harness'

const BASICS = readFileSync(join(import.meta.dirname, '../snippets/imported/tutorial/basics.oxsts'), 'utf-8')

interface Disk {
  // the file the open picker gives, the name the save picker gives
  opened: { name: string, text: string } | null
  savedAs: string
  writes: { name: string, text: string }[]
  suggested: string[]
}

// the browser's file pickers, answering from window.disk, which a test fills before it presses Open or Save
function stubPickers(): void {
  const disk: Disk = { opened: null, savedAs: 'saved.oxsts', writes: [], suggested: [] }
  const handleOf = (name: string, text: string) => {
    return {
      kind: 'file',
      name,
      getFile: async () => {
        return new File([text], name)
      },
      createWritable: async () => {
        let written = ''
        return {
          write: async (chunk: string) => {
            written += chunk
          },
          close: async () => {
            disk.writes.push({ name, text: written })
          },
        }
      },
    }
  }
  Object.assign(window, {
    disk,
    showOpenFilePicker: async () => {
      return disk.opened ? [handleOf(disk.opened.name, disk.opened.text)] : []
    },
    showSaveFilePicker: async (options: { suggestedName: string }) => {
      disk.suggested.push(options.suggestedName)
      return handleOf(disk.savedAs, '')
    },
  })
}

const NO_PICKERS = `${TEST_BROWSER} has no File System Access pickers, it opens and saves through the fallback`

function withoutPickers(): void {
  Object.defineProperty(window, 'showOpenFilePicker', { value: undefined })
  Object.defineProperty(window, 'showSaveFilePicker', { value: undefined })
}

// the file input's dialog, kept in window.fileDialog for the test to choose a file; Playwright's Firefox fires cancel before change when it fills a real one
function withFileDialog(): void {
  Object.defineProperty(window, 'showOpenFilePicker', { value: undefined })
  const click = HTMLInputElement.prototype.click
  HTMLInputElement.prototype.click = function clickOrOpenDialog(this: HTMLInputElement) {
    if (this.type !== 'file') {
      click.call(this)
      return
    }
    Object.assign(window, { fileDialog: this })
  }
}

async function diskOf(page: Page): Promise<Disk> {
  return page.evaluate(() => {
    return (window as unknown as { disk: Disk }).disk
  })
}

async function openFile(page: Page, name: string, text: string): Promise<void> {
  await page.evaluate(({ name: fileName, text: fileText }) => {
    (window as unknown as { disk: Disk }).disk.opened = { name: fileName, text: fileText }
  }, { name, text })
  await page.locator('.toolbar').getByRole('button', { name: 'Open' }).click()
}

async function typeAtTheEnd(page: Page, text: string): Promise<void> {
  await editor(page).lines.click()
  await page.keyboard.press((await editorKeysOf(page)).endOfDocument)
  await page.keyboard.type(text)
}

const unsavedMark = (page: Page) => {
  return page.locator('.unsaved-mark')
}

describe('opening a file', () => {
  test('through Open takes the picked file as the model, named by the file', async ({ ui, skip }) => {
    skip(TEST_BROWSER !== 'chromium', NO_PICKERS)
    const page = await ui.open('/oxsts/level-crossing', { before: stubPickers })
    await casesListed(page)

    await openFile(page, 'mine.oxsts', BASICS)

    await expect.poll(async () => {
      return page.title()
    }).toBe('mine.oxsts - OXSTS - Semantifyr Live')
    await casesListed(page)
    expect(await results(page).rows.count()).toBe(2)
  })

  test('through Open, where the browser has no file picker, takes the file chosen in the file dialog', async ({ ui }) => {
    const page = await ui.open('/oxsts/level-crossing', { before: withFileDialog })
    await casesListed(page)

    await page.locator('.toolbar').getByRole('button', { name: 'Open' }).click()
    await page.evaluate((text) => {
      const transfer = new DataTransfer()
      transfer.items.add(new File([text], 'mine.oxsts'))
      const dialog = (window as unknown as { fileDialog: HTMLInputElement }).fileDialog
      dialog.files = transfer.files
      dialog.dispatchEvent(new Event('change'))
    }, BASICS)

    await expect.poll(async () => {
      return page.title()
    }).toBe('mine.oxsts - OXSTS - Semantifyr Live')
  })

  test('by a drop opens the dropped file in its language', async ({ ui }) => {
    const page = await ui.open('/oxsts/basics')
    await casesListed(page)
    ui.backend.answer('discover', () => {
      return []
    })

    await page.evaluate(() => {
      const transfer = new DataTransfer()
      transfer.items.add(new File(['package dropped\n'], 'dropped.oxsts'))
      const target = document.querySelector('section.editor') ?? document.body
      for (const type of ['dragenter', 'dragover', 'drop']) {
        target.dispatchEvent(new DragEvent(type, { dataTransfer: transfer, bubbles: true, cancelable: true }))
      }
    })

    await expect.poll(async () => {
      return page.title()
    }).toBe('dropped.oxsts - OXSTS - Semantifyr Live')
  })
})

describe('saving', () => {
  test('with Ctrl+S from inside the editor writes the edits back into the opened file and clears the unsaved mark', async ({ ui, skip }) => {
    skip(TEST_BROWSER !== 'chromium', NO_PICKERS)
    const page = await ui.open('/oxsts/level-crossing', { before: stubPickers })
    await casesListed(page)
    await openFile(page, 'mine.oxsts', BASICS)
    await casesListed(page)

    await typeAtTheEnd(page, '\n// edited\n')
    await expect.poll(async () => {
      return unsavedMark(page).count()
    }).toBe(1)
    await page.keyboard.press('Control+s')

    await expect.poll(async () => {
      return (await diskOf(page)).writes.map((write) => {
        return write.name
      })
    }).toEqual(['mine.oxsts'])
    expect((await diskOf(page)).writes[0]!.text).toContain('// edited')
    await expect.poll(async () => {
      return unsavedMark(page).count()
    }).toBe(0)
  })

  test('with Save as asks for a place under the suggested name and saves there', async ({ ui, skip }) => {
    skip(TEST_BROWSER !== 'chromium', NO_PICKERS)
    const page = await ui.open('/oxsts/basics', { before: stubPickers })
    await casesListed(page)
    await typeAtTheEnd(page, '\n// edited\n')

    await page.getByRole('button', { name: 'Save options' }).click()
    await page.getByRole('menuitem', { name: /Save as/ }).click()

    await expect.poll(async () => {
      return (await diskOf(page)).writes.map((write) => {
        return write.name
      })
    }).toEqual(['saved.oxsts'])
    expect((await diskOf(page)).suggested).toEqual(['basics.oxsts'])
    await expect.poll(async () => {
      return page.title()
    }).toBe('saved.oxsts - OXSTS - Semantifyr Live')
  })

  test('downloads under the model\'s name where the browser has no save dialog', async ({ ui }) => {
    const page = await ui.open('/oxsts/basics', { before: withoutPickers })
    await casesListed(page)
    await typeAtTheEnd(page, '\n// edited\n')

    const download = page.waitForEvent('download')
    await page.locator('.toolbar').getByRole('button', { name: 'Save', exact: true }).click()

    expect((await download).suggestedFilename()).toBe('basics.oxsts')
  })
})

describe('leaving the page', () => {
  test('with edits not saved to a file brings the browser\'s warning, and without them none', async ({ ui }) => {
    const page = await ui.open('/oxsts/basics')
    await casesListed(page)
    await editorWorkerLoaded(page)
    const dialogs: string[] = []
    page.on('dialog', (dialog) => {
      dialogs.push(dialog.type())
      void dialog.dismiss()
    })

    await page.reload()
    await casesListed(page)
    await editorWorkerLoaded(page)
    expect(dialogs).toEqual([])
    await typeAtTheEnd(page, '\n// edited\n')
    await expect.poll(async () => {
      return unsavedMark(page).count()
    }).toBe(1)
    await page.reload({ timeout: 2000 }).catch(() => {})

    expect(dialogs).toEqual(['beforeunload'])
  })
})

describe('a share link with results', () => {
  test('opens with the sharer\'s verdicts, runs the verification and flags a verdict that came out differently', async ({ ui }) => {
    const page = await ui.open('/oxsts/level-crossing?library=gamma')
    await casesListed(page)
    await results(page).verifyAll.click()
    await expect.poll(async () => {
      return statesOf(results(page).rows, 'status-')
    }).toEqual(['failed', 'passed'])
    await page.locator('.toolbar').getByRole('button', { name: 'Share' }).click()
    const link = await page.locator('.share-popover .field .mono').innerText()
    expect(link).toContain('verdicts=')
    let release = (): void => {}
    const released = new Promise<void>((resolve) => {
      release = resolve
    })
    ui.backend.answer('verify', async (request, recorded) => {
      await released
      return request.caseId?.endsWith('TwoTrainsCanBeInTheCrossing') ? { ...recorded(), status: 'failed' } : recorded()
    })

    const receiver = await ui.open(new URL(link).pathname + new URL(link).search + new URL(link).hash)

    await expect.poll(async () => {
      return receiver.locator('.results .case-row .shared-tag').count()
    }).toBe(2)
    expect(await statesOf(results(receiver).rows, 'status-')).toEqual(['failed', 'passed'])
    release()
    await expect.poll(async () => {
      return results(receiver).rows.locator('.differs-tag').allInnerTexts()
    }).toEqual(['Differs, shared: Passed'])
    expect(await receiver.getByTestId('shared-notice').innerText()).toContain('1 of 2 results came out differently')
  })
})
