/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import type React from 'react'

import type { ActiveVerificationInfo, FlavorInfo, SessionInfo } from '../lib/api'
import { useStore } from '../lib/stores/useStore'
import { Icon } from '../ui/icons'
import { LanguageMark, VerdictMark } from '../ui/marks'
import { AdminIcon } from './adminIcons'
import type { AdminStore } from './adminStore'
import { durationLabel, filterSessions, kindLabel, languageOf } from './adminView'

function shortId(identifier: string): string {
  return identifier.length > 8 ? identifier.slice(0, 8) : identifier
}

function Counts({ session }: { session: SessionInfo }): React.JSX.Element {
  return (
    <span className="admin-counts">
      <span className={`admin-count${session.runningCount === 0 ? ' is-zero' : ''}`} title="Running">
        <VerdictMark status="running" />
        {session.runningCount}
      </span>
      <span className={`admin-count${session.queuedCount === 0 ? ' is-zero' : ''}`} title="Queued">
        <VerdictMark status="queued" />
        {session.queuedCount}
      </span>
    </span>
  )
}

function VerificationRow({ verification, store }: { verification: ActiveVerificationInfo, store: AdminStore }): React.JSX.Element {
  const running = verification.state === 'Running'
  return (
    <li className={`admin-verification status-${running ? 'running' : 'queued'}`}>
      <span className="admin-state">
        <VerdictMark status={running ? 'running' : 'queued'} />
        <span className="verdict-word">{running ? 'Running' : 'Queued'}</span>
      </span>
      <span className="tag">{verification.kind === 'Verify' ? 'Verifies' : 'Validates'}</span>
      <span className="mono admin-portfolio">{verification.portfolioId}</span>
      <span className="mono admin-identifier" title={verification.verificationId}>{verification.verificationId}</span>
      <span className="case-time">{running ? durationLabel(verification.elapsed) : ''}</span>
      <button
        type="button"
        className="button button-small button-outline"
        onClick={() => {
          void store.cancelVerification(verification.verificationId)
        }}
      >
        Cancel
      </button>
    </li>
  )
}

function Verifications({ session, store }: { session: SessionInfo, store: AdminStore }): React.JSX.Element {
  return (
    <div className="admin-details">
      {session.activeVerifications.length === 0
        ? <p className="admin-empty">No verification running or queued.</p>
        : (
          <ul className="admin-verifications" aria-label="Verifications">
            {session.activeVerifications.map((verification) => {
              return <VerificationRow key={verification.verificationId} verification={verification} store={store} />
            })}
          </ul>
        )}
      <p className="admin-directory">
        <span className="section-label">Working directory</span>
        <span className="mono" title={session.workingDirectory}>{session.workingDirectory}</span>
      </p>
    </div>
  )
}

interface SessionRowProps {
  session: SessionInfo
  flavors: readonly FlavorInfo[]
  open: boolean
  confirming: boolean
  store: AdminStore
}

function SessionRow({ session, flavors, open, confirming, store }: SessionRowProps): React.JSX.Element {
  const languageId = languageOf(flavors, session.flavorId)
  const flavor = flavors.find((candidate) => {
    return candidate.id === session.flavorId
  })
  return (
    <li className={`admin-session${open ? ' is-open' : ''}`} data-testid={`session-${session.sessionId}`}>
      <div className="admin-session-row">
        <button
          type="button"
          className="admin-session-toggle"
          aria-expanded={open}
          onClick={() => {
            store.toggleExpanded(session.sessionId)
          }}
        >
          <span className="admin-chevron"><AdminIcon name="chevron-right" /></span>
          <span className="tag" data-kind={session.kind}>{kindLabel(session.kind)}</span>
          <span className="admin-flavour" data-language={languageId} title={session.flavorId}>
            <LanguageMark languageId={languageId} />
            <span className="admin-flavour-name">{flavor?.displayName ?? session.flavorId}</span>
          </span>
          <span className="mono admin-identifier" title={session.sessionId}>{shortId(session.sessionId)}</span>
          <span className="mono admin-address">{session.remoteIp}</span>
          <span className="case-time">{durationLabel(session.uptime)}</span>
          <Counts session={session} />
        </button>
        <div className="admin-session-action">
          {confirming
            ? (
              <>
                <button
                  type="button"
                  className="button button-small button-danger"
                  onClick={() => {
                    void store.kill(session.sessionId)
                  }}
                >
                  Kill session
                </button>
                <button
                  type="button"
                  className="button button-small button-icon"
                  aria-label="Keep the session"
                  title="Keep the session"
                  onClick={() => {
                    store.askKill(null)
                  }}
                >
                  <Icon name="close" size="tiny" />
                </button>
              </>
            )
            : (
              <button
                type="button"
                className="button button-small button-outline"
                onClick={() => {
                  store.askKill(session.sessionId)
                }}
              >
                Kill
              </button>
            )}
        </div>
      </div>
      {open ? <Verifications session={session} store={store} /> : null}
    </li>
  )
}

export function Sessions({ store }: { store: AdminStore }): React.JSX.Element {
  const data = useStore(store.state, (state) => {
    return state.data
  })
  const flavors = useStore(store.state, (state) => {
    return state.flavors
  })
  const filter = useStore(store.state, (state) => {
    return state.filter
  })
  const expanded = useStore(store.state, (state) => {
    return state.expanded
  })
  const confirmingKill = useStore(store.state, (state) => {
    return state.confirmingKill
  })
  const sessions = data?.status.sessions ?? []
  const visible = filterSessions(sessions, flavors, filter)
  return (
    <section className="admin-panel admin-sessions" aria-label="Sessions">
      <div className="admin-panel-header">
        <h2 className="section-label">
          Sessions
          <span className="admin-total" data-testid="sessions-shown">
            {visible.length === sessions.length ? sessions.length : `${visible.length} of ${sessions.length}`}
          </span>
        </h2>
        <label className="field admin-filter">
          <input
            type="search"
            value={filter}
            placeholder="Filter by id, kind, language or address"
            aria-label="Filter sessions"
            onChange={(event) => {
              store.setFilter(event.target.value)
            }}
          />
        </label>
      </div>
      {sessions.length === 0
        ? <p className="admin-empty admin-empty-page" data-testid="sessions-empty">No sessions are open.</p>
        : visible.length === 0
          ? <p className="admin-empty admin-empty-page" data-testid="sessions-unmatched">{`No session matches "${filter.trim()}".`}</p>
          : (
            <>
              <div className="admin-session-row admin-session-head" aria-hidden="true">
                <div className="admin-session-toggle">
                  <span />
                  <span>Kind</span>
                  <span>Language</span>
                  <span>Id</span>
                  <span>Client</span>
                  <span>Uptime</span>
                  <span>Verifications</span>
                </div>
                <div className="admin-session-action" />
              </div>
              <ul className="admin-session-list">
                {visible.map((session) => {
                  return (
                    <SessionRow
                      key={session.sessionId}
                      session={session}
                      flavors={flavors}
                      open={expanded.has(session.sessionId)}
                      confirming={confirmingKill === session.sessionId}
                      store={store}
                    />
                  )
                })}
              </ul>
            </>
          )}
    </section>
  )
}
