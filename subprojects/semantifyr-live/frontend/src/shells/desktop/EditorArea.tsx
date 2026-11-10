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
import { libraryTabOf, MODEL_OXSTS_TAB_ID, modelOxstsTabOf } from '../../lib/workspace/libraryTabs'
import type { DocumentRecord, Live } from '../../lib/workspace/live'
import { useLive } from '../../lib/workspace/liveContext'
import { Icon } from '../../ui/icons'
import { LanguageMark } from '../../ui/marks'
import { documentLabel, useCurrentDocument, useFlavor } from '../hooks'
import { useShell } from '../shellContext'

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

function DocumentsMenu({ runtime, flavor, modelLabel }: { runtime: LanguageRuntime, flavor: LiveFlavor, modelLabel: string }): React.JSX.Element {
  const live = useLive()
  const hasModelOxsts = flavor.languageId !== 'oxsts'
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
      <span className="tabs-end">{runtime ? <DocumentsMenu runtime={runtime} flavor={flavor} modelLabel={modelLabel} /> : null}</span>
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
    })
    return () => {
      void binding.dispose()
    }
  }, [live, shell, flavor.languageId])

  return <div ref={hostReference} className="editor-host" data-testid="editor-host" />
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

  useEffect(() => {
    if (!runtime) {
      return
    }
    return setLibraryReader(runtime.readLibrary)
  }, [runtime])

  return (
    <section className={`editor${readOnly ? ' is-readonly' : ''}`} data-language={flavor?.languageId}>
      {flavor && document && modelLabel !== null ? <Tabs flavor={flavor} modelLabel={modelLabel} runtime={runtime} /> : <div className="tabs" />}
      <ReadOnlyBanner />
      <div className="editor-body">
        {flavor && document ? <EditorHost key={flavor.id} document={document} flavor={flavor} colorMode={colorMode} /> : null}
      </div>
    </section>
  )
}
