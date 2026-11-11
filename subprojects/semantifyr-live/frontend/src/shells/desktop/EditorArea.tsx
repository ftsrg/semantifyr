/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import type React from 'react'
import { useEffect, useRef } from 'react'
import { Menu } from '@base-ui/react/menu'

import type { LiveFlavor } from '../../examples'
import type { LanguageRuntime, LibraryKind } from '../../lib/language/types'
import { setLibraryReader } from '../../lib/session/monaco'
import { fileNameOf } from '../../lib/session/readOnlyDocuments'
import { bindTabbedEditor } from '../../lib/session/tabbedEditor'
import { useStore } from '../../lib/stores/useStore'
import type { ResolvedColorMode } from '../../lib/util/colorMode'
import { caseRowOf } from '../../lib/workspace/caseRows'
import { libraryTabOf, MODEL_OXSTS_TAB_ID, modelOxstsTabOf } from '../../lib/workspace/libraryTabs'
import type { DocumentRecord, Live } from '../../lib/workspace/live'
import { useCurrentVerification, useLive } from '../../lib/workspace/liveContext'
import { traceLanguageOf, type TraceView } from '../../trace/registry'
import { openTraceText } from '../../trace/TextView'
import { Icon } from '../../ui/icons'
import { LanguageMark } from '../../ui/marks'
import { documentLabel, useAddressedModel, useCurrentDocument, useFlavor } from '../hooks'
import { useShell } from '../shellContext'
import { DropTarget, UnsavedMark, useFileDrop, useKeepingEvents } from './keeping'
import { EditControl, modelLockOf } from '../touch'

const LIBRARY_ENTRY_LABELS: Readonly<Record<LibraryKind, string>> = {
  'library': 'Library',
  'semantic-library': 'OXSTS semantic library',
}

function modelOxstsText(text: string | null, message: string | null | undefined): string {
  return text ?? `// ${message ?? 'The OXSTS shows once the model compiles.'}\n`
}

// keeps the open OXSTS tab on the model's current OXSTS, which the runtime computes while the tab is open
function useModelOxstsTab(live: Live, runtime: LanguageRuntime | null, modelLabel: string | null): void {
  const open = useStore(live.tabs.state, (state) => {
    return state.tabs.some((tab) => {
      return tab.id === MODEL_OXSTS_TAB_ID
    })
  })
  useEffect(() => {
    if (!open || !runtime || modelLabel === null) {
      return
    }
    const follow = (): void => {
      const document = runtime.modelOxsts.get()
      live.tabs.update(MODEL_OXSTS_TAB_ID, modelOxstsText(document?.text ?? null, document?.message))
    }
    const unsubscribe = runtime.modelOxsts.subscribe(follow)
    follow()
    return unsubscribe
  }, [live, runtime, open, modelLabel])
}

// the selected case's trace text and the OXSTS of its trace, the documents the trace area's Open opens
function TraceEntry({ view, label, runtime }: { view: TraceView | undefined, label: string, runtime: LanguageRuntime }): React.JSX.Element | null {
  const live = useLive()
  const verification = useCurrentVerification(live)
  const selection = useStore(live.selection)
  if (!view?.source) {
    return null
  }
  const selected = verification.cases.find((caseState) => {
    return caseState.caseInfo.id === selection
  })
  const trace = selected?.trace ?? null
  const source = selected && trace ? view.source({ trace, caseName: caseRowOf(selected).name, runtime }) : null
  return (
    <Menu.Item
      className={`menu-item${source ? '' : ' is-disabled'}`}
      disabled={!source}
      data-document={view.id}
      onClick={() => {
        if (source) {
          void openTraceText(live, runtime, source)
        }
      }}
    >
      <Icon name="lock" size="small" />
      <span className="menu-item-label">{label}</span>
      <span className="menu-item-detail" title={source?.label}>{source ? source.label : 'select a case with a trace'}</span>
    </Menu.Item>
  )
}

function DocumentsMenu({ runtime, flavor, modelLabel }: { runtime: LanguageRuntime, flavor: LiveFlavor, modelLabel: string }): React.JSX.Element {
  const live = useLive()
  const hasModelOxsts = flavor.languageId !== 'oxsts'
  const traceViews = traceLanguageOf(flavor.languageId).views
  const viewOf = (id: string): TraceView | undefined => {
    return traceViews.find((view) => {
      return view.id === id
    })
  }
  return (
    <Menu.Root>
      <Menu.Trigger className="button button-icon" title="Read-only documents" aria-label="Read-only documents">
        <Icon name="files" size="small" />
      </Menu.Trigger>
      <Menu.Portal>
        <Menu.Positioner className="floating" side="bottom" align="end" sideOffset={6}>
          <Menu.Popup className="menu documents-menu">
            <Menu.Group>
              <Menu.GroupLabel className="menu-heading">Open read-only</Menu.GroupLabel>
              <TraceEntry view={viewOf('own')} label="Trace of the selected case" runtime={runtime} />
              {hasModelOxsts
                ? (
                  <Menu.Item
                    className="menu-item"
                    onClick={() => {
                      const document = runtime.modelOxsts.get()
                      live.tabs.open(modelOxstsTabOf(modelLabel, modelOxstsText(document?.text ?? null, document?.message)))
                    }}
                  >
                    <Icon name="lock" size="small" />
                    <span className="menu-item-label">OXSTS of the model</span>
                    <span className="menu-item-detail">{modelLabel.replace(/\.[^.]*$/, '.oxsts')}</span>
                  </Menu.Item>
                )
                : null}
              <TraceEntry view={viewOf('witness')} label={viewOf('own') ? 'OXSTS of the trace' : 'Trace of the selected case'} runtime={runtime} />
              {runtime.libraryDocuments.map((library) => {
                return (
                  <Menu.Item
                    key={library.uri}
                    className="menu-item"
                    onClick={() => {
                      live.tabs.open(libraryTabOf(library.uri, library.languageId))
                    }}
                  >
                    <Icon name="lock" size="small" />
                    <span className="menu-item-label">{LIBRARY_ENTRY_LABELS[library.kind]}</span>
                    <span className="menu-item-detail">{decodeURIComponent(fileNameOf(library.uri))}</span>
                  </Menu.Item>
                )
              })}
            </Menu.Group>
          </Menu.Popup>
        </Menu.Positioner>
      </Menu.Portal>
    </Menu.Root>
  )
}

function Tabs({ flavor, modelLabel, runtime }: { flavor: LiveFlavor, modelLabel: string, runtime: LanguageRuntime | null }): React.JSX.Element {
  const live = useLive()
  const tabs = useStore(live.tabs.state)
  return (
    <div className="tabs" role="tablist" aria-label="Documents">
      <button
        type="button"
        className="tab tab-model"
        role="tab"
        aria-selected={tabs.active === null}
        onClick={() => {
          live.tabs.show(null)
        }}
      >
        <LanguageMark languageId={flavor.languageId} />
        <span className="tab-label">{modelLabel}</span>
        <UnsavedMark />
      </button>
      {tabs.tabs.map((tab) => {
        return (
          <div
            key={tab.id}
            className="tab is-readonly"
            role="tab"
            tabIndex={0}
            aria-selected={tabs.active === tab.id}
            title={`${tab.label}, read-only`}
            onClick={() => {
              live.tabs.show(tab.id)
            }}
            onKeyDown={(event) => {
              if (event.key === 'Enter' || event.key === ' ') {
                event.preventDefault()
                live.tabs.show(tab.id)
              }
            }}
          >
            <Icon name="lock" />
            <span className="tab-label">{tab.label}</span>
            <button
              type="button"
              className="tab-close"
              title="Close"
              aria-label={`Close ${tab.label}`}
              onClick={(event) => {
                event.stopPropagation()
                live.tabs.close(tab.id)
              }}
            >
              <Icon name="close" />
            </button>
          </div>
        )
      })}
      <span className="tabs-spacer" />
      <span className="tabs-end">
        <EditControl className="button-small" />
        {runtime ? <DocumentsMenu runtime={runtime} flavor={flavor} modelLabel={modelLabel} /> : null}
      </span>
    </div>
  )
}

function ReadOnlyBanner(): React.JSX.Element | null {
  const live = useLive()
  const banner = useStore(live.tabs.state, (state) => {
    return state.tabs.find((tab) => {
      return tab.id === state.active
    })?.banner ?? null
  })
  if (banner === null) {
    return null
  }
  return (
    <div className="readonly-banner">
      <Icon name="lock" />
      <span>{`Read-only. ${banner}`}</span>
      <button
        type="button"
        className="button-link"
        onClick={() => {
          live.tabs.show(null)
        }}
      >
        Back to the model
      </button>
    </div>
  )
}

function EditorHost({ document, flavor, colorMode }: { document: DocumentRecord, flavor: LiveFlavor, colorMode: ResolvedColorMode }): React.JSX.Element {
  const live = useLive()
  const shell = useShell()
  const hostReference = useRef<HTMLDivElement>(null)
  const mountedWith = useRef({ document, colorMode })

  useEffect(() => {
    const host = hostReference.current
    if (!host) {
      return
    }
    const { document: mounted, colorMode: mode } = mountedWith.current
    const binding = bindTabbedEditor({
      host,
      live,
      document: { uri: mounted.uri, languageId: flavor.languageId, text: live.currentText() ?? mounted.text },
      colorMode: mode,
      onCursor: (position) => {
        shell.cursor.set(position)
      },
      modelLocked: modelLockOf(shell),
    })
    return () => {
      void binding.dispose()
    }
  }, [live, shell, flavor.languageId])

  return <div ref={hostReference} className="editor-host" data-testid="editor-host" />
}

// while the server does not answer, the model the address names is shown as text; it knows no language yet
function WaitingModel(): React.JSX.Element | null {
  const live = useLive()
  const waiting = useStore(live.catalog, (catalog) => {
    return catalog.status === 'waiting'
  })
  const model = useAddressedModel(live)
  if (!waiting || model.text === null) {
    return null
  }
  return (
    <div className="editor-scroll">
      <div className="code" data-language={model.languageId} data-testid="waiting-model">
        {model.text.replace(/\n+$/, '').split('\n').map((line, index) => {
          const indent = /^ */.exec(line)?.[0].length ?? 0
          return (
            <div key={index} className="code-line is-plain">
              <span className="line-number">{index + 1}</span>
              <span />
              <span className="line-text" style={{ '--indent': indent } as React.CSSProperties}>{line.slice(indent) || ' '}</span>
            </div>
          )
        })}
      </div>
    </div>
  )
}

export function EditorArea({ colorMode }: { colorMode: ResolvedColorMode }): React.JSX.Element {
  const live = useLive()
  const flavor = useFlavor(live)
  const document = useCurrentDocument(live)
  const runtime = useStore(live.workspace, (workspace) => {
    return workspace.runtime
  })
  const readOnly = useStore(live.tabs.state, (state) => {
    return state.active !== null
  })
  const modelLabel = document && flavor ? documentLabel(document, flavor) : null
  useModelOxstsTab(live, runtime, modelLabel)
  useKeepingEvents()
  const drop = useFileDrop()

  useEffect(() => {
    if (!runtime) {
      return
    }
    return setLibraryReader(runtime.readLibrary)
  }, [runtime])

  return (
    <section className={`editor${readOnly ? ' is-readonly' : ''}`} data-language={flavor?.languageId} {...drop.handlers}>
      {flavor && document && modelLabel !== null ? <Tabs flavor={flavor} modelLabel={modelLabel} runtime={runtime} /> : <div className="tabs" />}
      <ReadOnlyBanner />
      <div className="editor-body">
        {flavor && document ? <EditorHost key={flavor.languageId} document={document} flavor={flavor} colorMode={colorMode} /> : <WaitingModel />}
        {drop.dragging ? <DropTarget /> : null}
      </div>
    </section>
  )
}
