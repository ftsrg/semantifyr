/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import type React from 'react'

import { useStore } from '../lib/stores/useStore'
import { formatIsoDuration } from '../lib/util/duration'
import { LanguageMark } from '../ui/marks'
import type { AdminStore } from './adminStore'
import { flavourRows, sessionsPerFlavor } from './adminView'

interface FactProps {
  label: string
  value: string
  mono?: boolean
}

function Fact({ label, value, mono }: FactProps): React.JSX.Element {
  return (
    <div className="admin-fact">
      <dt>{label}</dt>
      <dd className={mono ? 'mono' : undefined}>{value}</dd>
    </div>
  )
}

function Group({ title, children }: { title: string, children: React.ReactNode }): React.JSX.Element {
  return (
    <section className="admin-group" aria-label={title}>
      <h3 className="section-label">{title}</h3>
      {children}
    </section>
  )
}

export function Deployed({ store }: { store: AdminStore }): React.JSX.Element | null {
  const data = useStore(store.state, (state) => {
    return state.data
  })
  const flavors = useStore(store.state, (state) => {
    return state.flavors
  })
  const portfolios = useStore(store.state, (state) => {
    return state.portfolios
  })
  if (data === null) {
    return null
  }
  const { info, config, status } = data
  const counts = sessionsPerFlavor(status.sessions, flavors)
  return (
    <aside className="admin-panel admin-deployed" aria-label="Deployed">
      <div className="admin-panel-header">
        <h2 className="section-label">Deployed</h2>
      </div>
      <div className="admin-deployed-body">
        <Group title="Backend">
          <dl>
            <Fact label="Version" value={info.version} mono />
            <Fact label="Commit" value={info.commit} mono />
            <Fact label="Built" value={info.buildTime} />
          </dl>
        </Group>
        <Group title="Frontend">
          <dl>
            <Fact label="Version" value={__VERSION__} mono />
            <Fact label="Commit" value={__GIT_COMMIT__} mono />
            <Fact label="Built" value={__BUILD_TIME__} />
          </dl>
        </Group>
        <Group title="Limits">
          <dl>
            <Fact label="Sessions" value={String(config.sessionManager.maxSessionsGlobal)} />
            <Fact label="Verifier concurrency" value={String(config.verification.concurrency)} />
            <Fact label="Verifier time" value={formatIsoDuration(config.verification.timeout)} />
          </dl>
        </Group>
        <Group title="Portfolios">
          <ul className="admin-rows">
            {portfolios.map((portfolio) => {
              const engines = config.portfolios.find((candidate) => {
                return candidate.id === portfolio.id
              })?.engines ?? []
              return (
                <li key={portfolio.id} className={`admin-row${portfolio.available ? '' : ' is-unavailable'}`}>
                  <span className="admin-row-name" title={portfolio.id}>{portfolio.displayName}</span>
                  <span className="admin-engines">
                    {engines.map((engine) => {
                      return <span key={engine} className="tag">{engine}</span>
                    })}
                  </span>
                  <span className="admin-row-meta">{portfolio.available ? '' : 'unavailable'}</span>
                </li>
              )
            })}
          </ul>
        </Group>
        <Group title="Flavours">
          <ul className="admin-rows">
            {flavourRows(flavors).map((flavor) => {
              const count = counts.get(flavor.id) ?? 0
              return (
                <li key={flavor.id} className={`admin-row${count === 0 ? ' is-idle' : ''}`}>
                  <span className="admin-row-name" title={flavor.id}>
                    <LanguageMark languageId={flavor.languageId} />
                    {flavor.displayName}
                  </span>
                  <span className="admin-row-count" title={`${count} ${count === 1 ? 'session' : 'sessions'}`}>{count}</span>
                </li>
              )
            })}
          </ul>
        </Group>
      </div>
    </aside>
  )
}
