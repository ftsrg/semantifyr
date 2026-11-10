/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import type React from 'react'
import { useState } from 'react'
import { Popover } from '@base-ui/react/popover'
import { liveEditorUrl } from '@semantifyr/editor-common'

import type { Live } from '../../lib/workspace/live'
import { useLive } from '../../lib/workspace/liveContext'
import { encodeSharedResults, SHARED_RESULTS_PARAMETER, sharedResultsOf } from '../../lib/workspace/sharedResults'
import { Icon } from '../../ui/icons'

type CopyState =
  | 'copied'
  | 'failed'

export async function shareLink(live: Live, withResults = false): Promise<string | null> {
  const { flavor, documentKey } = live.workspace.get()
  const document = documentKey === null ? undefined : live.documents.get().get(documentKey)
  if (!flavor || !document) {
    return null
  }
  const content = live.currentText() ?? document.text
  const link = await liveEditorUrl(`${window.location.origin}${window.location.pathname}`, {
    flavor: flavor.id,
    example: document.shared ? undefined : document.example.id,
    code: document.shared || content !== document.example.code ? content : undefined,
  })
  const results = withResults && documentKey !== null ? sharedResultsOf(live.verification.get().get(documentKey), live.selection.get()) : null
  if (results === null) {
    return link
  }
  const address = new URL(link)
  address.searchParams.set(SHARED_RESULTS_PARAMETER, await encodeSharedResults(results))
  return address.toString()
}

function hasResults(live: Live): boolean {
  const { documentKey } = live.workspace.get()
  return documentKey !== null && sharedResultsOf(live.verification.get().get(documentKey), null) !== null
}

async function copy(link: string): Promise<CopyState> {
  try {
    await navigator.clipboard.writeText(link)
    return 'copied'
  } catch {
    return 'failed'
  }
}

export function SharePopover(): React.JSX.Element {
  const live = useLive()
  const [link, setLink] = useState<string | null>(null)
  const [state, setState] = useState<CopyState | null>(null)
  const [withResults, setWithResults] = useState(true)
  const [available, setAvailable] = useState(false)

  const build = async (carryResults: boolean): Promise<void> => {
    const built = await shareLink(live, carryResults)
    setLink(built)
    setState(built === null ? null : await copy(built))
  }

  const open = async (): Promise<void> => {
    const carries = hasResults(live)
    setAvailable(carries)
    setWithResults(carries)
    await build(carries)
  }

  const choose = (carryResults: boolean): void => {
    setWithResults(carryResults)
    void build(carryResults)
  }

  return (
    <Popover.Root
      onOpenChange={(opened) => {
        if (opened) {
          void open()
        }
      }}
    >
      <Popover.Trigger className="button button-quiet">
        <Icon name="link" />
        Share
      </Popover.Trigger>
      <Popover.Portal>
        <Popover.Positioner className="floating" side="bottom" align="end" sideOffset={6}>
          <Popover.Popup className="menu share-popover">
            <div className="share-copied">
              <Icon name={state === 'failed' ? 'alert' : 'check'} />
              <Popover.Title className="popover-title">{state === 'failed' ? 'Copy the link' : 'Link copied'}</Popover.Title>
            </div>
            <div className="field">
              <span className="mono">{link ?? ''}</span>
              <button
                type="button"
                className="button button-icon"
                title="Copy"
                aria-label="Copy the link"
                onClick={() => {
                  if (link !== null) {
                    void copy(link).then(setState)
                  }
                }}
              >
                <Icon name="copy" size="small" />
              </button>
            </div>
            <div className="share-options" role="radiogroup" aria-label="What the link carries">
              <button
                type="button"
                role="radio"
                className="share-option"
                aria-checked={withResults}
                disabled={!available}
                onClick={() => {
                  choose(true)
                }}
              >
                <span className={`radio${withResults ? ' is-checked' : ''}`} />
                <span>With results</span>
                <span className="share-option-detail">
                  {available ? 'Carries the model, the selected case and each verdict. The receiver sees your verdicts while the run reproduces them.' : 'No result to carry yet; verify the cases first.'}
                </span>
              </button>
              <button
                type="button"
                role="radio"
                className="share-option"
                aria-checked={!withResults}
                onClick={() => {
                  choose(false)
                }}
              >
                <span className={`radio${withResults ? '' : ' is-checked'}`} />
                <span>Model only</span>
                <span className="share-option-detail">A shorter link; the receiver presses Verify all.</span>
              </button>
            </div>
            <div className="share-note">Everything is in the link itself; nothing is stored on the server.</div>
          </Popover.Popup>
        </Popover.Positioner>
      </Popover.Portal>
    </Popover.Root>
  )
}
