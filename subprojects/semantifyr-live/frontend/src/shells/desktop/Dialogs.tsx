/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import type React from 'react'
import { useEffect, useState } from 'react'
import { Dialog } from '@base-ui/react/dialog'

import { createApi } from '../../lib/api/rest'
import type { InfoResponse, SessionInfo } from '../../lib/api/types'
import type { LspMetrics } from '../../lib/session/lspMetrics'
import { useStore } from '../../lib/stores/useStore'
import { formatDuration, formatIsoDuration } from '../../lib/util/duration'
import { useLive } from '../../lib/workspace/liveContext'
import { ABOUT, DEVELOPER, PRIVACY_URL, SHORT_NAME, SOURCE_CODE_URL, WEBSITE_URL } from '../../texts'
import { Icon } from '../../ui/icons'
import { Logo } from '../../ui/marks'
import { useLanguage } from '../hooks'

const PAGE_START = Date.now()

// the phone's menu draws the triggers as its own entries
interface TriggerProps {
  className?: string
  children?: React.ReactNode
}

export function AboutDialog({ className, children }: TriggerProps): React.JSX.Element {
  return (
    <Dialog.Root>
      <Dialog.Trigger className={className ?? 'button button-quiet'}>
        {children ?? (
          <>
            <Icon name="info" />
            About
          </>
        )}
      </Dialog.Trigger>
      <Dialog.Portal>
        <Dialog.Backdrop className="scrim" />
        <Dialog.Popup className="dialog">
          <div className="dialog-header">
            <span className="brand">
              <Logo />
              <span className="dialog-product">{SHORT_NAME}</span>
            </span>
            <Dialog.Close className="button button-icon" aria-label="Close">
              <Icon name="close" />
            </Dialog.Close>
          </div>
          <div className="dialog-body">
            <Dialog.Title className="dialog-lead">{ABOUT.sentences.join(' ')}</Dialog.Title>
            <a className="button-link" href={PRIVACY_URL} target="_blank" rel="noreferrer">{ABOUT.privacy}</a>
            <div className="dialog-links">
              <a className="button button-outline button-small" href={WEBSITE_URL} target="_blank" rel="noreferrer">
                <Icon name="globe" size="tiny" />
                {ABOUT.website}
              </a>
              <a className="button button-outline button-small" href={SOURCE_CODE_URL} target="_blank" rel="noreferrer">
                <Icon name="code" size="tiny" />
                {ABOUT.sourceCode}
              </a>
            </div>
          </div>
          <div className="dialog-footer">
            <span>{`Version ${__VERSION__}`}</span>
            <span>EPL-2.0</span>
          </div>
        </Dialog.Popup>
      </Dialog.Portal>
    </Dialog.Root>
  )
}

interface DeveloperInfo {
  backend: InfoResponse | null
  backendFailed: boolean
  session: SessionInfo | null
  metrics: LspMetrics | null
  now: number
}

function Row({ label, value, mono }: { label: string, value: string, mono?: boolean }): React.JSX.Element {
  return (
    <div className="developer-row">
      <span className="developer-label">{label}</span>
      <span className={mono ? 'mono' : undefined}>{value}</span>
    </div>
  )
}

function milliseconds(value: number | null | undefined): string {
  return value === null || value === undefined ? '-' : `${value} ms`
}

function DeveloperBody(): React.JSX.Element {
  const live = useLive()
  const connection = useStore(live.connection)
  const runtime = useStore(live.workspace, (workspace) => {
    return workspace.runtime
  })
  const language = useLanguage(live)
  const [info, setInfo] = useState<DeveloperInfo>({ backend: null, backendFailed: false, session: null, metrics: null, now: Date.now() })

  useEffect(() => {
    let cancelled = false
    createApi(window.location.origin).fetchInfo().then((backend) => {
      if (!cancelled) {
        setInfo((previous) => {
          return { ...previous, backend }
        })
      }
    }, () => {
      if (!cancelled) {
        setInfo((previous) => {
          return { ...previous, backendFailed: true }
        })
      }
    })
    const refresh = (): void => {
      setInfo((previous) => {
        return { ...previous, metrics: runtime?.metrics() ?? null, now: Date.now() }
      })
      runtime?.api.getSessionInfo().then((session) => {
        if (!cancelled) {
          setInfo((previous) => {
            return { ...previous, session }
          })
        }
      }, () => {
        if (!cancelled) {
          setInfo((previous) => {
            return { ...previous, session: null }
          })
        }
      })
    }
    refresh()
    const timer = setInterval(refresh, 2000)
    return () => {
      cancelled = true
      clearInterval(timer)
    }
  }, [runtime])

  const { backend, session, metrics, now } = info
  return (
    <div className="developer-body">
      <span className="section-label">Frontend</span>
      <Row label="Language" value={language?.presentation.name ?? '-'} />
      <Row label="Version" value={__VERSION__} mono />
      <Row label="Commit" value={__GIT_COMMIT__} mono />
      <Row label="Built" value={__BUILD_TIME__} />
      <Row label="Page uptime" value={formatDuration(Math.floor((now - PAGE_START) / 1000))} />
      <span className="section-label">{DEVELOPER.server}</span>
      {backend
        ? (
          <>
            <Row label="Version" value={backend.version} mono />
            <Row label="Commit" value={backend.commit} mono />
            <Row label="Built" value={backend.buildTime} />
            <Row label="Uptime" value={formatIsoDuration(backend.uptime)} />
            <Row label="Sessions" value={`${backend.activeSessions} / ${backend.maxSessions}`} />
          </>
        )
        : <Row label="Status" value={info.backendFailed ? 'unreachable' : 'loading...'} />}
      <span className="section-label">Language server connection</span>
      <Row label="Status" value={connection.status} />
      <Row label="Uptime" value={connection.connectedSince === null ? '-' : formatDuration(Math.floor((now - connection.connectedSince) / 1000))} />
      <Row label="Reconnects" value={String(connection.reconnectCount)} />
      <Row label="Requests" value={metrics ? String(metrics.requestCount) : '-'} />
      <Row label="Notifications" value={metrics ? String(metrics.notificationCount) : '-'} />
      <Row label="Errors" value={metrics ? String(metrics.errorCount) : '-'} />
      <Row label="Last response" value={milliseconds(metrics?.lastResponseTimeMs)} mono />
      <Row label="Average response" value={milliseconds(metrics?.avgResponseTimeMs)} mono />
      <span className="section-label">Session</span>
      <Row label="Session id" value={session?.sessionId ?? '-'} mono />
      <Row label="Uptime" value={session ? formatIsoDuration(session.uptime) : '-'} />
      <Row label="Verifications" value={session ? String(session.activeVerifications.length) : '-'} />
    </div>
  )
}

export function DeveloperDialog({ className, children }: TriggerProps): React.JSX.Element {
  const [open, setOpen] = useState(false)
  return (
    <Dialog.Root open={open} onOpenChange={setOpen}>
      <Dialog.Trigger className={className ?? 'button button-icon'} title="Developer panel" aria-label="Developer panel">
        {children ?? <Icon name="terminal" />}
      </Dialog.Trigger>
      <Dialog.Portal>
        <Dialog.Backdrop className="scrim" />
        <Dialog.Popup className="dialog developer-dialog">
          <div className="dialog-header">
            <Dialog.Title className="popover-title">Developer panel</Dialog.Title>
            <Dialog.Close className="button button-icon" aria-label="Close">
              <Icon name="close" />
            </Dialog.Close>
          </div>
          {open ? <DeveloperBody /> : null}
        </Dialog.Popup>
      </Dialog.Portal>
    </Dialog.Root>
  )
}
