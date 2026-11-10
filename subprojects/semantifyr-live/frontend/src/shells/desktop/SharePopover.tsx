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
import { Icon } from '../../ui/icons'

type CopyState =
  | 'copied'
  | 'failed'

async function shareLink(live: Live): Promise<string | null> {
  const { flavor, documentKey } = live.workspace.get()
  const document = documentKey === null ? undefined : live.documents.get().get(documentKey)
  if (!flavor || !document) {
    return null
  }
  const content = live.currentText() ?? document.text
  return liveEditorUrl(`${window.location.origin}${window.location.pathname}`, {
    flavor: flavor.id,
    example: document.shared ? undefined : document.example.id,
    code: document.shared || content !== document.example.code ? content : undefined,
  })
}

async function copy(link: string): Promise<CopyState> {
  try {
    await navigator.clipboard.writeText(link)
    return 'copied'
  } catch {
    return 'failed'
  }
}

// the share link carries the model; its verdicts are a later step
export function SharePopover(): React.JSX.Element {
  const live = useLive()
  const [link, setLink] = useState<string | null>(null)
  const [state, setState] = useState<CopyState | null>(null)

  const open = async (): Promise<void> => {
    const built = await shareLink(live)
    setLink(built)
    setState(built === null ? null : await copy(built))
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
            <div className="share-note">Everything is in the link itself; nothing is stored on the server.</div>
          </Popover.Popup>
        </Popover.Positioner>
      </Popover.Portal>
    </Popover.Root>
  )
}
