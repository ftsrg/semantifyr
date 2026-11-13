/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import type React from 'react'
import { useState } from 'react'
import { Popover } from '@base-ui/react/popover'

import type { LiveFlavor } from '../../examples'
import { SYSML_LIBRARY_PREFIX } from '../../lib/language/libraries'
import type { LanguageRuntime, LibraryDocument, LibraryKind } from '../../lib/language/types'
import { fileNameOf } from '../../lib/session/readOnlyDocuments'
import { useStore } from '../../lib/stores/useStore'
import { caseRowOf } from '../../lib/workspace/caseRows'
import { libraryTabOf, openModelOxsts } from '../../lib/workspace/libraryTabs'
import { useCurrentVerification, useLive } from '../../lib/workspace/liveContext'
import { traceLanguageOf, type TraceView } from '../../trace/registry'
import { openTraceText } from '../../trace/TextView'
import { DOCUMENTS } from '../../texts'
import { Icon } from '../../ui/icons'

const LIBRARY_HEADINGS: Readonly<Record<LibraryKind, string>> = {
  'standard-library': 'SysML v2 standard library',
  'library': 'OXSTS library',
  'semantic-library': 'Semantic library, in OXSTS',
}

const LIBRARY_ORDER: readonly LibraryKind[] = [
  'standard-library',
  'library',
  'semantic-library',
]

// more files than the menu shows at once get a filter
const FILTERED_FROM = 16

// the folder of a file inside its library, empty for one at the library's root
function folderOf(library: LibraryDocument): string {
  const path = decodeURIComponent(library.uri.startsWith(SYSML_LIBRARY_PREFIX) ? library.uri.slice(SYSML_LIBRARY_PREFIX.length) : fileNameOf(library.uri))
  return path.includes('/') ? path.slice(0, path.lastIndexOf('/')) : ''
}

function matches(filter: string, ...texts: string[]): boolean {
  const needle = filter.trim().toLowerCase()
  return needle === '' || texts.some((text) => {
    return text.toLowerCase().includes(needle)
  })
}

function Entry({ label, detail, disabled, document, onOpen }: { label: string, detail: string, disabled?: boolean, document?: string, onOpen: () => void }): React.JSX.Element {
  return (
    <button
      type="button"
      className={`menu-item${disabled === true ? ' is-disabled' : ''}`}
      disabled={disabled === true}
      data-document={document}
      onClick={onOpen}
    >
      <Icon name="lock" size="small" />
      <span className="menu-item-label">{label}</span>
      {detail === '' ? null : <span className="menu-item-detail" title={detail}>{detail}</span>}
    </button>
  )
}

// the selected case's trace text and the OXSTS of its trace, the documents the trace area's Open opens
function TraceEntry({ view, label, runtime, close }: { view: TraceView | undefined, label: string, runtime: LanguageRuntime, close: () => void }): React.JSX.Element | null {
  const live = useLive()
  const verification = useCurrentVerification(live)
  const selection = useStore(live.selection)
  const documentKey = useStore(live.workspace, (workspace) => {
    return workspace.documentKey
  })
  if (!view?.source) {
    return null
  }
  const selected = verification.cases.find((caseState) => {
    return caseState.caseInfo.id === selection
  })
  const trace = selected?.trace ?? null
  const source = selected && trace ? view.source({ trace, caseName: caseRowOf(selected).name, runtime }) : null
  return (
    <Entry
      label={label}
      detail={source ? source.label : DOCUMENTS.noTrace}
      disabled={!source}
      document={view.id}
      onOpen={() => {
        if (selected && trace && source && documentKey !== null) {
          void openTraceText(live, runtime, source, { documentKey, caseId: selected.caseInfo.id, view: view.id, shown: trace })
          close()
        }
      }}
    />
  )
}

function Group({ heading, children }: { heading: string, children: React.ReactNode }): React.JSX.Element {
  return (
    <div className="documents-group" role="group" aria-label={heading}>
      <div className="menu-heading">{heading}</div>
      {children}
    </div>
  )
}

export function DocumentsMenu({ runtime, flavor, modelLabel }: { runtime: LanguageRuntime, flavor: LiveFlavor, modelLabel: string }): React.JSX.Element {
  const live = useLive()
  const [open, setOpen] = useState(false)
  const [filter, setFilter] = useState('')
  const libraries = useStore(runtime.libraryDocuments)
  const hasModelOxsts = flavor.languageId !== 'oxsts'
  const traceViews = traceLanguageOf(flavor.languageId).views
  const viewOf = (id: string): TraceView | undefined => {
    return traceViews.find((view) => {
      return view.id === id
    })
  }
  const close = (): void => {
    setOpen(false)
    setFilter('')
  }
  const modelOxstsLabel = modelLabel.replace(/\.[^.]*$/, '.oxsts')
  const filtered = libraries.length > FILTERED_FROM
  const shown = libraries.filter((library) => {
    return matches(filter, decodeURIComponent(fileNameOf(library.uri)), folderOf(library))
  })
  return (
    <Popover.Root
      open={open}
      onOpenChange={(next) => {
        if (next) {
          setOpen(true)
        } else {
          close()
        }
      }}
    >
      <Popover.Trigger className="button button-small button-quiet documents-button" title="Read-only documents" aria-label="Read-only documents">
        <Icon name="book" size="small" />
        <span className="documents-button-label">Documents</span>
        <Icon name="chevron-down" size="small" />
      </Popover.Trigger>
      <Popover.Portal>
        <Popover.Positioner className="floating" side="bottom" align="end" sideOffset={6}>
          <Popover.Popup className="menu documents-menu">
            <Popover.Title className="menu-heading documents-title">Open read-only</Popover.Title>
            {filtered
              ? (
                <input
                  className="input documents-filter"
                  type="search"
                  placeholder="Filter the library files"
                  aria-label="Filter the library files"
                  value={filter}
                  onChange={(event) => {
                    setFilter(event.target.value)
                  }}
                />
              )
              : null}
            <div className="documents-list">
              <Group heading="Selected case">
                <TraceEntry view={viewOf('own')} label={DOCUMENTS.trace} runtime={runtime} close={close} />
                <TraceEntry view={viewOf('witness')} label={DOCUMENTS.witness} runtime={runtime} close={close} />
              </Group>
              {hasModelOxsts
                ? (
                  <Group heading="Model">
                    <Entry
                      label="OXSTS of the model"
                      detail={modelOxstsLabel}
                      document="model-oxsts"
                      onOpen={() => {
                        openModelOxsts(live, runtime, modelLabel)
                        close()
                      }}
                    />
                  </Group>
                )
                : null}
              {LIBRARY_ORDER.map((kind) => {
                const files = shown.filter((library) => {
                  return library.kind === kind
                })
                if (files.length === 0) {
                  return null
                }
                return (
                  <Group key={kind} heading={LIBRARY_HEADINGS[kind]}>
                    {files.map((library) => {
                      return (
                        <Entry
                          key={library.uri}
                          label={decodeURIComponent(fileNameOf(library.uri))}
                          detail={folderOf(library)}
                          document={kind}
                          onOpen={() => {
                            live.tabs.open(libraryTabOf(library.uri, library.languageId))
                            close()
                          }}
                        />
                      )
                    })}
                  </Group>
                )
              })}
              {filtered && shown.length === 0 ? <div className="documents-empty">No library file matches.</div> : null}
            </div>
          </Popover.Popup>
        </Popover.Positioner>
      </Popover.Portal>
    </Popover.Root>
  )
}
