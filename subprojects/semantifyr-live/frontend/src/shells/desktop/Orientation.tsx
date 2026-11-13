/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import type React from 'react'
import type { ServedExample } from '@semantifyr/editor-common/examples'

import { findExample } from '../../examples'
import { presentationOf } from '../../lib/language/presentation'
import { useStore } from '../../lib/stores/useStore'
import type { AddressProblem, Live } from '../../lib/workspace/live'
import { useCurrentVerification, useLive } from '../../lib/workspace/liveContext'
import { casesSentence, EXAMPLE_PAGE, exampleRights, websitePage } from '../../texts'
import { Icon } from '../../ui/icons'
import { useCurrentDocument, useFlavor } from '../hooks'
import { useShell } from '../shellContext'
import { dismissOrientation } from '../shellState'

export function addressProblemText(live: Live, problem: AddressProblem): string {
  const flavor = live.catalog.get().registry?.flavorOf(problem.flavorId)
  const languageName = flavor ? presentationOf(flavor.languageId).name : problem.flavorId
  const exampleLabel = (flavor ? findExample(flavor, problem.exampleId)?.label : undefined) ?? problem.exampleId
  switch (problem.kind) {
    case 'results':
      return `The results in the link could not be read. Opened ${exampleLabel} without them.`
    case 'model':
      return `The model in the link could not be read. Opened ${exampleLabel} in its place.`
    case 'language':
      return `The address names the language "${problem.requested}", which Live does not have. Opened ${languageName} with the ${exampleLabel} in its place.`
    case 'library':
      return `The address names the library "${problem.requested}", which ${languageName} does not have. Opened ${languageName} without a library, with the ${exampleLabel}, in its place.`
    case 'example':
      return `The address names the example "${problem.requested}", which ${languageName} does not have. Opened ${exampleLabel} in its place.`
  }
}

function AddressError(): React.JSX.Element | null {
  const live = useLive()
  const problem = useStore(live.address)
  if (!problem) {
    return null
  }
  return (
    <div className="orientation is-error" role="alert">
      <Icon name="alert" size="small" />
      <span className="orientation-text">{addressProblemText(live, problem)}</span>
      <button
        type="button"
        className="button button-icon"
        title="Dismiss"
        aria-label="Dismiss"
        onClick={() => {
          live.address.set(null)
        }}
      >
        <Icon name="close" size="small" />
      </button>
    </div>
  )
}

// where the example comes from, and the page that explains it
function ExampleSource({ served }: { served: ServedExample | null }): React.JSX.Element {
  if (served === null) {
    return <span className="orientation-source" />
  }
  const rights = exampleRights(served.origin, served.holder, served.licence)
  return (
    <span className="orientation-source">
      <span className="orientation-rights" title={rights}>{rights}</span>
      <a href={websitePage(served.page)} target="_blank" rel="noreferrer">{EXAMPLE_PAGE}</a>
    </span>
  )
}

export function Orientation(): React.JSX.Element {
  const live = useLive()
  const shell = useShell()
  const document = useCurrentDocument(live)
  const flavor = useFlavor(live)
  const verification = useCurrentVerification(live)
  const dismissed = useStore(shell.dismissedOrientations)
  const visible = document !== null && flavor !== null && !document.shared && !dismissed.has(document.key)
  const example = visible ? findExample(flavor, document.example.id) : undefined
  const count = verification.discovery === 'discovered' ? verification.cases.length : null
  // once the model was verified, by the visitor or by the link that opened it, the button needs no pointing at
  const verified = verification.phase !== 'idle' || verification.cases.some((caseState) => {
    return caseState.resultStatus !== undefined
  })
  return (
    <>
      <AddressError />
      {visible && example
        ? (
          <div className="orientation">
            <span className="orientation-mark" />
            <span className="orientation-text">
              <strong>{`${example.label}.`}</strong>
              {casesSentence(count)}
              {verified
                ? null
                : (
                  <>
                    {' Press '}
                    <strong>Verify all</strong>
                    {count === 1 ? ' to check it.' : ' to check them.'}
                  </>
                )}
            </span>
            <ExampleSource served={example.served} />
            <button
              type="button"
              className="button button-icon"
              title="Dismiss"
              aria-label="Dismiss"
              onClick={() => {
                dismissOrientation(shell, document.key)
              }}
            >
              <Icon name="close" size="small" />
            </button>
          </div>
        )
        : null}
    </>
  )
}
