/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import { describe, expect, it } from 'vitest'

import { deploymentLinks, filterSessions, sessionsPerFlavor } from '../../admin/adminView'
import type { FlavorInfo } from '../../lib/api'
import { servedFlavors } from '../fixtures/flavors'
import { fakeSession } from '../fixtures/sessions'

// Goal: what the filter matches, the per-flavour count and the sibling hosts of the deployment

const SESSIONS = [
  fakeSession({ sessionId: 'aaaa1111', flavorId: 'oxsts-with-sysmlv2-library', languageId: 'sysml', kind: 'full', remoteIp: '10.0.0.1' }),
  fakeSession({ sessionId: 'dddd4444', flavorId: 'oxsts-with-sysmlv2-library', kind: 'full', remoteIp: '10.0.0.4' }),
  fakeSession({ sessionId: 'bbbb2222', flavorId: 'oxsts', kind: 'embed', remoteIp: '10.0.0.2' }),
  fakeSession({ sessionId: 'cccc3333', flavorId: 'oxsts', kind: 'edit-only', remoteIp: '192.168.1.9' }),
]

const FLAVOR_LIST: readonly FlavorInfo[] = servedFlavors

function matching(filter: string): string[] {
  return filterSessions(SESSIONS, FLAVOR_LIST, filter).map((session) => {
    return session.sessionId
  })
}

describe('the session filter', () => {
  it('keeps every session without a filter', () => {
    expect(matching('  ')).toHaveLength(4)
  })

  it('matches the id, the kind, the language or flavour and the client address', () => {
    expect(matching('bbbb')).toEqual(['bbbb2222'])
    expect(matching('EMBED')).toEqual(['bbbb2222'])
    expect(matching('edit only')).toEqual(['cccc3333'])
    expect(matching('SysML v2')).toEqual(['aaaa1111', 'dddd4444'])
    expect(matching('sysml')).toEqual(['aaaa1111', 'dddd4444'])
    expect(matching('OXSTS with the SysML')).toEqual(['dddd4444'])
    expect(matching('192.168')).toEqual(['cccc3333'])
  })

  it('matches nothing for an unknown word', () => {
    expect(matching('nothing')).toEqual([])
  })
})

describe('the per-flavour count', () => {
  it('counts the sessions of each flavour, a SysML v2 session as its own row', () => {
    const counts = sessionsPerFlavor(SESSIONS, FLAVOR_LIST)
    expect(counts.get('oxsts')).toBe(2)
    expect(counts.get('sysml')).toBe(1)
    expect(counts.get('oxsts-with-sysmlv2-library')).toBe(1)
    expect(counts.get('gamma')).toBeUndefined()
  })
})

describe('the deployment links', () => {
  it('points Grafana and the sign-out at the sibling hosts', () => {
    const links = deploymentLinks({ protocol: 'https:', hostname: 'live.semantifyr.org', origin: 'https://live.semantifyr.org' })
    expect(links.grafana).toBe('https://grafana.semantifyr.org/')
    expect(links.signOut).toBe('https://auth.semantifyr.org/oauth2/sign_out?rd=https%3A%2F%2Flive.semantifyr.org')
  })

  it('has none for a host without siblings', () => {
    for (const hostname of ['localhost', '127.0.0.1', 'live.local']) {
      const links = deploymentLinks({ protocol: 'http:', hostname, origin: `http://${hostname}` })
      expect(links.grafana, hostname).toBeNull()
      expect(links.signOut, hostname).toBeNull()
    }
  })
})
