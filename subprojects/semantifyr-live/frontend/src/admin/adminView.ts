/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import type { FlavorInfo, SessionInfo } from '../lib/api'
import { SYSML_FLAVOR_INFO } from '../lib/session/sysmlBridge'
import { formatDuration, isoDurationToMs } from '../lib/util/duration'

const KIND_LABELS: Readonly<Record<string, string>> = {
  'full': 'Full',
  'embed': 'Embed',
  'edit-only': 'Edit only',
}

export function durationLabel(iso: string): string {
  return formatDuration(Math.floor(isoDurationToMs(iso) / 1000))
}

export function kindLabel(kind: string): string {
  return KIND_LABELS[kind] ?? kind
}

// the served flavours and SysML v2, which the page serves over the SysML v2 library flavour
export function flavourRows(flavors: readonly FlavorInfo[]): FlavorInfo[] {
  return [...flavors, SYSML_FLAVOR_INFO]
}

// a session whose editor speaks another language than its flavour is listed under that language's row
export function flavourRowOf(flavors: readonly FlavorInfo[], session: SessionInfo): FlavorInfo | undefined {
  const rows = flavourRows(flavors)
  const flavor = rows.find((row) => {
    return row.id === session.flavorId
  })
  if (flavor?.languageId === session.languageId) {
    return flavor
  }
  return rows.find((row) => {
    return row.id === session.languageId
  }) ?? flavor
}

export function filterSessions(sessions: readonly SessionInfo[], flavors: readonly FlavorInfo[], filter: string): SessionInfo[] {
  const needle = filter.trim().toLowerCase()
  if (needle === '') {
    return [...sessions]
  }
  return sessions.filter((session) => {
    return [
      session.sessionId,
      session.kind,
      kindLabel(session.kind),
      session.flavorId,
      session.languageId,
      flavourRowOf(flavors, session)?.displayName ?? '',
      session.remoteIp,
    ].some((field) => {
      return field.toLowerCase().includes(needle)
    })
  })
}

export function sessionsPerFlavor(sessions: readonly SessionInfo[], flavors: readonly FlavorInfo[]): ReadonlyMap<string, number> {
  const counts = new Map<string, number>()
  for (const session of sessions) {
    const row = flavourRowOf(flavors, session)?.id ?? session.flavorId
    counts.set(row, (counts.get(row) ?? 0) + 1)
  }
  return counts
}

export interface DeploymentLinks {
  grafana: string | null
  signOut: string | null
}

// the deployment serves Grafana and the sign-in proxy beside the live server, on sibling hosts
function siblingHost(hostname: string, label: string): string | null {
  const labels = hostname.split('.')
  if (labels.length < 3 || labels.every((part) => {
    return /^\d+$/.test(part)
  })) {
    return null
  }
  return [label, ...labels.slice(1)].join('.')
}

export function deploymentLinks(location: Pick<Location, 'protocol' | 'hostname' | 'origin'>): DeploymentLinks {
  const grafanaHost = siblingHost(location.hostname, 'grafana')
  const authHost = siblingHost(location.hostname, 'auth')
  return {
    grafana: grafanaHost === null ? null : `${location.protocol}//${grafanaHost}/`,
    signOut: authHost === null ? null : `${location.protocol}//${authHost}/oauth2/sign_out?rd=${encodeURIComponent(location.origin)}`,
  }
}
