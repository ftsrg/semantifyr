/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import type { FlavorInfo, SessionInfo } from '../lib/api'
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

export function languageOf(flavors: readonly FlavorInfo[], flavorId: string): string {
  return flavors.find((flavor) => {
    return flavor.id === flavorId
  })?.languageId ?? flavorId
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
      languageOf(flavors, session.flavorId),
      session.remoteIp,
    ].some((field) => {
      return field.toLowerCase().includes(needle)
    })
  })
}

export function sessionsPerFlavor(sessions: readonly SessionInfo[]): ReadonlyMap<string, number> {
  const counts = new Map<string, number>()
  for (const session of sessions) {
    counts.set(session.flavorId, (counts.get(session.flavorId) ?? 0) + 1)
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
