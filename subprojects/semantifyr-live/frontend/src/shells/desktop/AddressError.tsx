/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import type React from 'react'

import { findExample } from '../../examples'
import { presentationOf } from '../../lib/language/presentation'
import { useStore } from '../../lib/stores/useStore'
import type { AddressProblem, Live } from '../../lib/workspace/live'
import { useLive } from '../../lib/workspace/liveContext'
import { Icon } from '../../ui/icons'

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

export function AddressError(): React.JSX.Element | null {
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
