/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import type React from 'react'
import { FLAME_PATH } from '@semantifyr/editor-common/marks'

import type { ValidationMark as ValidationState } from '../lib/workspace/caseRows'

export function Logo(): React.JSX.Element {
  return (
    <svg className="logo" viewBox="8 22 2952 566" role="img" aria-label="Semantifyr">
      <path className="logo-flame" transform="translate(1060 9)" d={FLAME_PATH} />
      <text x="15.955" y="451">S</text>
      <text x="306.42" y="451">e</text>
      <text x="618.09" y="451">m</text>
      <text x="1456.52" y="451">ntifyr</text>
    </svg>
  )
}

export type CloudState =
  | 'connected'
  | 'busy'
  | 'connecting'
  | 'offline'
  | 'failure'

const CLOUD_SHAPE = 'M7.1 18.6h10.4a4.15 4.15 0 0 0 .55-8.26A5.7 5.7 0 0 0 7.2 9a4.8 4.8 0 0 0-.1 9.6z'

export function Cloud({ state }: { state: CloudState }): React.JSX.Element {
  switch (state) {
    case 'connected':
      return (
        <svg className="cloud" viewBox="0 0 24 24" aria-hidden="true">
          <path className="cloud-body" d={CLOUD_SHAPE} />
        </svg>
      )
    case 'busy':
      return (
        <svg className="cloud" viewBox="0 0 24 24" aria-hidden="true">
          <path className="cloud-body" d={CLOUD_SHAPE} />
          <circle className="cloud-dot" cx="9" cy="14.4" r="1.05" />
          <circle className="cloud-dot" cx="12.3" cy="14.4" r="1.05" />
          <circle className="cloud-dot" cx="15.6" cy="14.4" r="1.05" />
        </svg>
      )
    case 'connecting':
      return (
        <svg className="cloud" viewBox="0 0 24 24" aria-hidden="true">
          <path className="cloud-outline" d={CLOUD_SHAPE} />
        </svg>
      )
    case 'offline':
    case 'failure':
      return (
        <svg className="cloud" viewBox="0 0 24 24" aria-hidden="true">
          <path className="cloud-outline" d={CLOUD_SHAPE} />
          <path className="cloud-slash" d="M4 4.5l16 15" />
        </svg>
      )
  }
}

const VALIDATION_WORDS: Readonly<Record<ValidationState, string>> = {
  'validation-queued': 'validation queued',
  'validating': 'validating',
  'validated': 'validated',
  'validation-failed': 'validation failed',
}

const SHIELD = 'M8 1.7l5 1.8v4c0 3.1-2.1 5.4-5 6.8-2.9-1.4-5-3.7-5-6.8v-4z'

export function ValidationMark({ state, detail }: { state: ValidationState, detail?: string | null }): React.JSX.Element {
  const word = VALIDATION_WORDS[state]
  const title = detail ? `${word}: ${detail}` : word
  return (
    <svg className={`validation-mark is-${state}`} viewBox="0 0 16 16" role="img" aria-label={word}>
      <title>{title}</title>
      {state === 'validating' || state === 'validation-queued' ? <path className="shield" d={SHIELD} strokeDasharray="3 2.4" /> : <path className="shield" d={SHIELD} />}
      {state === 'validated' ? <path className="glyph" d="M5.6 8.1l1.7 1.7 3.2-3.5" /> : null}
      {state === 'validation-failed'
        ? (
          <>
            <path className="glyph" d="M8 5.3v3" />
            <circle className="glyph-fill" cx="8" cy="10.5" r=".8" />
          </>
        )
        : null}
    </svg>
  )
}
