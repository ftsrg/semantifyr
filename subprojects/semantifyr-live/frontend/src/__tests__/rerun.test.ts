/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import { describe, expect, it } from 'vitest'
import {
  markEdited,
  runAllVerifications,
  type LspClient,
  type VerificationState,
} from '../lib/verification'
import { sampleCase } from './fixtures/cases'
import { failedResult, passedResult } from './fixtures/verification-results'

function holder(initial: VerificationState): {
  state: () => VerificationState
  setState: (next: VerificationState | ((previous: VerificationState) => VerificationState)) => void
} {
  let state = initial
  return {
    state: () => state,
    setState: (next) => {
      state = typeof next === 'function' ? next(state) : next
    },
  }
}

const failedCase: VerificationState = {
  phase: 'done',
  discovery: 'discovered',
  cases: [
    { caseInfo: sampleCase('a'), status: 'failed', resultStatus: 'failed', trace: failedResult.trace!, message: 'property fails' },
  ],
}

function hangingClient(): { client: LspClient, answer: () => void } {
  let answer = (): void => {}
  return {
    client: {
      sendRequest: () => {
        return new Promise((resolve) => {
          answer = () => {
            resolve(passedResult)
          }
        })
      },
      sendNotification: () => {},
    },
    answer: () => {
      answer()
    },
  }
}

describe('a run over cases that have results', () => {
  it('returns a cancelled rerun to the result it had', async () => {
    const { client } = hangingClient()
    const { state, setState } = holder(failedCase)

    const handle = runAllVerifications(client, 'oxsts.case.verify', 'file:///x', [sampleCase('a')], setState, () => {
      return () => {}
    })
    await new Promise((resolve) => {
      return setTimeout(resolve, 0)
    })
    handle.cancel()

    expect(state().cases[0]).toMatchObject({ status: 'failed', trace: failedResult.trace, message: 'property fails' })
  })

  it('marks a result outdated when the model was edited while it was verified', async () => {
    const { client, answer } = hangingClient()
    const { state, setState } = holder(failedCase)

    const handle = runAllVerifications(client, 'oxsts.case.verify', 'file:///x', [sampleCase('a')], setState, () => {
      return () => {}
    })
    await new Promise((resolve) => {
      return setTimeout(resolve, 0)
    })
    setState(markEdited)
    answer()
    await handle.done

    expect(state().cases[0]).toMatchObject({ status: 'passed', outdated: true })
  })
})
