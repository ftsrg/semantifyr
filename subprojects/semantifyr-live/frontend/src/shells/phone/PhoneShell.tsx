/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import type React from 'react'
import { useEffect, useLayoutEffect, useState } from 'react'

import { presentationOf } from '../../lib/language/presentation'
import { useStore } from '../../lib/stores/useStore'
import type { ResolvedColorMode } from '../../lib/util/colorMode'
import { caseRowOf } from '../../lib/workspace/caseRows'
import { useCurrentVerification, useLive } from '../../lib/workspace/liveContext'
import { Icon, type IconName } from '../../ui/icons'
import { LanguageMark, Logo } from '../../ui/marks'
import { ConnectionIndicator } from '../desktop/ConnectionIndicator'
import { useKeepingEvents } from '../desktop/keeping'
import { useCurrentDocument, useFlavor } from '../hooks'
import { useShell } from '../shellContext'
import { verifyAll } from '../shellState'
import { StatusContent, useStatusLine } from '../statusLine'
import { useTouchInput } from '../touch'
import type { TraceSlot } from '../traceSlot'
import { AddressError, ModelPane, ResultsPane, TracePane, useInitialized, type Pane } from './panes'
import { ChooserSheet, MenuSheet, PortfolioSheet, ProblemsSheet, type Overlay } from './sheets'

import './phone.css'

interface Props {
  colorMode: ResolvedColorMode
  trace?: TraceSlot
}

function Top({ onOverlay }: { onOverlay: (overlay: Overlay) => void }): React.JSX.Element {
  const live = useLive()
  const flavor = useFlavor(live)
  const document = useCurrentDocument(live)
  return (
    <header className="phone-top">
      <span className="phone-brand">
        <Logo />
        <ConnectionIndicator />
      </span>
      {flavor && document
        ? (
          <button
            type="button"
            className="model-chooser"
            data-language={flavor.languageId}
            aria-label="Choose a model"
            onClick={() => {
              onOverlay('chooser')
            }}
          >
            <LanguageMark languageId={flavor.languageId} />
            <span className="model-chooser-text">
              <span className="model-chooser-language">{presentationOf(flavor.languageId).name}</span>
              <span className="model-chooser-example">{document.shared ? 'Shared model' : document.example.label}</span>
            </span>
            <Icon name="chevron-down" size="small" />
          </button>
        )
        : <span className="model-chooser" />}
      <button
        type="button"
        className="phone-icon-button"
        title="Menu"
        aria-label="Menu"
        onClick={() => {
          onOverlay('menu')
        }}
      >
        <Icon name="more" />
      </button>
    </header>
  )
}

function VerifyButton({ onRun }: { onRun: () => void }): React.JSX.Element {
  const live = useLive()
  const shell = useShell()
  const verification = useCurrentVerification(live)
  const pending = useStore(live.pendingVerifyAll)
  if (verification.phase === 'running' || pending) {
    return (
      <button
        type="button"
        className="button button-cancel phone-verify"
        onClick={() => {
          live.cancelVerification()
        }}
      >
        <Icon name="stop" size="small" />
        Cancel
      </button>
    )
  }
  return (
    <button
      type="button"
      className="button button-primary phone-verify"
      onClick={() => {
        onRun()
        verifyAll(live, shell)
      }}
    >
      <Icon name="play" size="small" />
      Verify all
    </button>
  )
}

function ProblemsButton({ onOpen }: { onOpen: () => void }): React.JSX.Element {
  const live = useLive()
  const problems = useStore(live.problems)
  const errors = problems.filter((problem) => {
    return problem.severity === 'error'
  }).length
  const warnings = problems.filter((problem) => {
    return problem.severity === 'warning'
  }).length
  return (
    <button
      type="button"
      className={`phone-problems${errors + warnings > 0 ? ' has-problems' : ''}`}
      aria-label={`Problems in the model: ${errors} errors, ${warnings} warnings`}
      onClick={onOpen}
    >
      <Icon name="error" size="small" />
      {errors}
      <Icon name="alert" size="small" />
      {warnings}
    </button>
  )
}

function Bottom({ pane, onPane, onProblems }: { pane: Pane, onPane: (pane: Pane) => void, onProblems: () => void }): React.JSX.Element {
  const live = useLive()
  const line = useStatusLine()
  const verification = useCurrentVerification(live)
  const initialized = useInitialized()
  const catalog = useStore(live.catalog, (state) => {
    return state.status
  })
  const rows = verification.cases.map(caseRowOf)
  const hasTrace = rows.some((row) => {
    return row.hasTrace
  })
  const failed = rows.find((row) => {
    return row.status === 'failed'
  })
  const tab = (target: Pane, icon: IconName, label: string, extra: React.ReactNode = null): React.JSX.Element => {
    return (
      <button
        type="button"
        className="phone-tab"
        aria-pressed={pane === target}
        disabled={target === 'trace' && !hasTrace}
        onClick={() => {
          onPane(target)
        }}
      >
        <span className="phone-tab-icon">
          <Icon name={icon} />
          {extra}
        </span>
        <span>{label}</span>
      </button>
    )
  }
  return (
    <footer className="phone-bottom">
      {!initialized && catalog !== 'waiting' ? <div className="progress-indeterminate" /> : null}
      <div className={`phone-status is-${line.status.kind}`} role="status" data-testid="status-message">
        <StatusContent line={line} />
        <ProblemsButton onOpen={onProblems} />
      </div>
      <nav className="phone-bar" aria-label="Panes">
        {tab('model', 'code', 'Model')}
        {tab('results', 'list', 'Results', failed ? <span className={`tab-dot status-failed${failed.outdated ? ' is-outdated' : ''}`} /> : null)}
        {tab('trace', 'diagram', 'Trace')}
        <VerifyButton
          onRun={() => {
            if (pane === 'trace') {
              onPane('results')
            }
          }}
        />
      </nav>
    </footer>
  )
}

interface Viewport {
  height: number
  top: number
}

// the on-screen keyboard shrinks the visual viewport, not the layout; the shell follows the visual one
function useVisualViewport(): Viewport | null {
  const [viewport, setViewport] = useState<Viewport | null>(null)
  useEffect(() => {
    const visual = window.visualViewport
    if (!visual) {
      return
    }
    const update = (): void => {
      setViewport({ height: visual.height, top: visual.offsetTop })
    }
    update()
    visual.addEventListener('resize', update)
    visual.addEventListener('scroll', update)
    return () => {
      visual.removeEventListener('resize', update)
      visual.removeEventListener('scroll', update)
    }
  }, [])
  return viewport
}

export default function PhoneShell({ colorMode, trace }: Props): React.JSX.Element {
  const live = useLive()
  const shell = useShell()
  const flavor = useFlavor(live)
  const [pane, setPane] = useState<Pane>('model')
  const [overlay, setOverlay] = useState<Overlay | null>(null)
  const touch = useTouchInput()
  const editing = useStore(shell.editing) && touch
  const viewport = useVisualViewport()
  useKeepingEvents()

  useLayoutEffect(() => {
    document.documentElement.dataset.shell = 'phone'
    return () => {
      delete document.documentElement.dataset.shell
    }
  }, [])

  // a revealed place and an opened read-only document are shown in the model's pane
  useEffect(() => {
    const unsubscribeReveal = live.reveal.subscribe(() => {
      setPane('model')
    })
    const unsubscribeTabs = live.tabs.state.subscribe(() => {
      if (live.tabs.state.get().active !== null) {
        setPane('model')
      }
    })
    return () => {
      unsubscribeReveal()
      unsubscribeTabs()
    }
  }, [live])

  const close = (): void => {
    setOverlay(null)
  }
  const style = viewport ? { height: viewport.height, top: viewport.top } : undefined
  return (
    <div className={`phone${editing ? ' is-editing' : ''}`} data-language={flavor?.languageId} data-pane={pane} data-testid="phone-shell" style={style}>
      <Top onOverlay={setOverlay} />
      <AddressError />
      <ModelPane colorMode={colorMode} hidden={pane !== 'model'} />
      {pane === 'results' ? <ResultsPane onPane={setPane} /> : null}
      {pane === 'trace' ? <TracePane slot={trace} /> : null}
      {editing
        ? null
        : (
          <Bottom
            pane={pane}
            onPane={setPane}
            onProblems={() => {
              setOverlay('problems')
            }}
          />
        )}
      <ChooserSheet open={overlay === 'chooser'} onClose={close} />
      <MenuSheet
        open={overlay === 'menu'}
        onClose={close}
        colorMode={colorMode}
        onPortfolios={(kind) => {
          setOverlay(kind)
        }}
      />
      <PortfolioSheet kind={overlay === 'verification' || overlay === 'validation' ? overlay : null} onClose={close} />
      <ProblemsSheet
        open={overlay === 'problems'}
        onClose={close}
        onReveal={() => {
          close()
          setPane('model')
        }}
      />
    </div>
  )
}
