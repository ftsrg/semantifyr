/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import type React from 'react'
import { Menu } from '@base-ui/react/menu'
import { useEffect, useState } from 'react'

import { useStore } from '../../lib/stores/useStore'
import { caseRowOf, type CaseRow } from '../../lib/workspace/caseRows'
import { browserFileAccess, fileOfDrop, type FileAccess } from '../../lib/workspace/files'
import { openFileInto, saveDocument, saveDocumentAs, saveWayOf } from '../../lib/workspace/keeping'
import type { Live } from '../../lib/workspace/live'
import { compareWithShared, rowsWithSharedVerdicts, type SharedResults } from '../../lib/workspace/sharedResults'
import { useCurrentVerification, useLive } from '../../lib/workspace/liveContext'
import { Icon } from '../../ui/icons'
import { documentLabel, useCurrentDocument, useFlavor } from '../hooks'

import './keeping.css'

let access: FileAccess | null = null

function fileAccess(): FileAccess {
  access ??= browserFileAccess()
  return access
}

export function pickFile(live: Live): void {
  void fileAccess().pick().then((opened) => {
    if (opened) {
      openFileInto(live, fileAccess(), opened)
    }
  })
}

export function OpenButton(): React.JSX.Element {
  const live = useLive()
  return (
    <button
      type="button"
      className="button button-quiet"
      onClick={() => {
        pickFile(live)
      }}
    >
      <Icon name="open" />
      Open
    </button>
  )
}

export function UnsavedMark(): React.JSX.Element | null {
  const live = useLive()
  const unsaved = useStore(live.unsaved)
  return unsaved ? <span className="unsaved-mark" title="Unsaved changes" /> : null
}

// Ctrl+S saves, and leaving with edits that no file holds brings the browser's warning
export function useKeepingEvents(): void {
  const live = useLive()
  const flavor = useFlavor(live)
  const document = useCurrentDocument(live)
  const suggestedName = flavor && document ? documentLabel(document, flavor) : null
  useEffect(() => {
    const save = (event: KeyboardEvent): void => {
      if ((event.ctrlKey || event.metaKey) && !event.altKey && event.key.toLowerCase() === 's') {
        event.preventDefault()
        if (suggestedName !== null) {
          void (event.shiftKey ? saveDocumentAs : saveDocument)(live, fileAccess(), suggestedName)
        }
      }
    }
    const leave = (event: BeforeUnloadEvent): void => {
      if (live.hasUnsaved()) {
        event.preventDefault()
      }
    }
    window.addEventListener('keydown', save)
    window.addEventListener('beforeunload', leave)
    return () => {
      window.removeEventListener('keydown', save)
      window.removeEventListener('beforeunload', leave)
    }
  }, [live, suggestedName])
}

function hasFiles(event: React.DragEvent): boolean {
  return event.dataTransfer.types.includes('Files')
}

export function useFileDrop(): {
  dragging: boolean
  handlers: Pick<React.DOMAttributes<HTMLElement>, 'onDragEnterCapture' | 'onDragOverCapture' | 'onDragLeaveCapture' | 'onDropCapture'>
} {
  const live = useLive()
  const [depth, setDepth] = useState(0)
  return {
    dragging: depth > 0,
    handlers: {
      onDragEnterCapture: (event) => {
        if (hasFiles(event)) {
          event.preventDefault()
          setDepth((previous) => {
            return previous + 1
          })
        }
      },
      onDragOverCapture: (event) => {
        if (hasFiles(event)) {
          event.preventDefault()
        }
      },
      onDragLeaveCapture: (event) => {
        if (hasFiles(event)) {
          setDepth((previous) => {
            return Math.max(0, previous - 1)
          })
        }
      },
      onDropCapture: (event) => {
        if (!hasFiles(event)) {
          return
        }
        event.preventDefault()
        event.stopPropagation()
        setDepth(0)
        void fileOfDrop(event.dataTransfer).then((opened) => {
          if (opened) {
            openFileInto(live, fileAccess(), opened)
          }
        })
      },
    },
  }
}

export function DropTarget(): React.JSX.Element {
  return (
    <div className="drop-target" data-testid="drop-target">
      <div className="drop-file">
        <Icon name="file" size="small" />
      </div>
      <Icon name="upload" />
      <div className="drop-target-title">Drop to open the file</div>
      <div className="drop-target-detail">A .sysml, .gamma or .oxsts file opens in its language. Live opens single files; imports of other files are not resolved.</div>
    </div>
  )
}

export function useCaseRows(): { rows: CaseRow[], results: SharedResults | null, running: boolean } {
  const live = useLive()
  const verification = useCurrentVerification(live)
  const results = useStore(live.sharedResults)
  const pending = useStore(live.pendingVerifyAll)
  const running = pending || verification.phase === 'running'
  return { rows: rowsWithSharedVerdicts(verification.cases.map(caseRowOf), results, running), results, running }
}

export function SharedNotice({ rows, results, running }: { rows: readonly CaseRow[], results: SharedResults, running: boolean }): React.JSX.Element {
  const { compared, differing } = compareWithShared(rows, results)
  const text = running
    ? 'The verdicts marked shared are the sharer\'s; each is replaced when your run produces its own.'
    : compared === 0
      ? 'The run ended without a result to compare with the shared ones.'
      : differing === 0
        ? `All ${compared} ${compared === 1 ? 'result' : 'results'} came out as shared.`
        : `${differing} of ${compared} ${compared === 1 ? 'result' : 'results'} came out differently from the shared one.`
  return (
    <div className="results-notice is-shared" data-testid="shared-notice">
      <Icon name="link" />
      <span>{`Opened from a shared link. ${text}`}</span>
    </div>
  )
}

function saveNote(live: ReturnType<typeof useLive>, name: string | null): string {
  const file = live.documents.get().get(live.workspace.get().documentKey ?? '')?.file ?? null
  switch (saveWayOf(file, fileAccess())) {
    case 'write':
      return `Save writes back to ${file?.name ?? ''} on your disk.`
    case 'dialog':
      return 'Save asks where on your disk to put the file.'
    case 'download':
      return `Save downloads ${name ?? 'the file'}.`
  }
}

export function SaveSplit(): React.JSX.Element {
  const live = useLive()
  const flavor = useFlavor(live)
  const document = useCurrentDocument(live)
  useStore(live.documents)
  const name = flavor && document ? documentLabel(document, flavor) : null
  const run = (save: typeof saveDocument): void => {
    if (name !== null) {
      void save(live, fileAccess(), name)
    }
  }
  return (
    <span className="save-split">
      <button
        type="button"
        className="button button-quiet"
        onClick={() => {
          run(saveDocument)
        }}
      >
        <Icon name="save" />
        Save
      </button>
      <Menu.Root>
        <Menu.Trigger className="button button-quiet" title="Save options" aria-label="Save options">
          <Icon name="chevron-down" size="small" />
        </Menu.Trigger>
        <Menu.Portal>
          <Menu.Positioner className="floating" side="bottom" align="end" sideOffset={6}>
            <Menu.Popup className="menu save-menu">
              <Menu.Item
                className="menu-item"
                onClick={() => {
                  run(saveDocument)
                }}
              >
                <span>
                  <Icon name="save" size="small" />
                </span>
                <span className="menu-item-label">Save</span>
                <span className="kbd">Ctrl S</span>
              </Menu.Item>
              <Menu.Item
                className="menu-item"
                onClick={() => {
                  run(saveDocumentAs)
                }}
              >
                <span>
                  <Icon name="save-as" size="small" />
                </span>
                <span className="menu-item-label">Save as...</span>
                <span className="kbd">Ctrl Shift S</span>
              </Menu.Item>
              <Menu.Separator className="menu-divider" />
              <div className="menu-item-detail save-note">{saveNote(live, name)}</div>
            </Menu.Popup>
          </Menu.Positioner>
        </Menu.Portal>
      </Menu.Root>
    </span>
  )
}

export function UnsavedLine(): React.JSX.Element {
  const live = useLive()
  const flavor = useFlavor(live)
  const document = useCurrentDocument(live)
  const name = flavor && document ? documentLabel(document, flavor) : ''
  const way = saveWayOf(document?.file ?? null, fileAccess())
  return (
    <>
      Unsaved changes. <span className="kbd">Ctrl S</span>
      {way === 'dialog' ? ' asks where to save' : ` ${way === 'write' ? 'saves to' : 'downloads'} ${name}`}
    </>
  )
}
