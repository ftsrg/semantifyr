/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import type React from 'react'
import { useState } from 'react'
import { Popover } from '@base-ui/react/popover'

import { changeSettings } from '../../lib/stores/settings'
import { useStore } from '../../lib/stores/useStore'
import { portfolioChoices, portfolioName, type PortfolioChoice } from '../../lib/workspace/portfolioChoices'
import { useLive } from '../../lib/workspace/liveContext'
import { Icon } from '../../ui/icons'

type PortfolioKind =
  | 'verification'
  | 'validation'

function PortfolioItem({ choice, checked, onChoose }: { choice: PortfolioChoice, checked: boolean, onChoose: () => void }): React.JSX.Element {
  return (
    <button type="button" className="menu-item portfolio-item" role="menuitemradio" aria-checked={checked} onClick={onChoose}>
      <span className={`radio${checked ? ' is-checked' : ''}`} />
      <span className="menu-item-label">{choice.name}</span>
      {choice.detail !== null ? <span className="menu-item-detail">{choice.detail}</span> : null}
    </button>
  )
}

export function SettingsPopover(): React.JSX.Element {
  const live = useLive()
  const [kind, setKind] = useState<PortfolioKind>('verification')
  const { portfolios } = useStore(live.catalog)
  const settings = useStore(live.settings)
  const verificationId = live.verificationPortfolioId() ?? null
  const validationId = settings.validationPortfolioId
  const current = kind === 'verification' ? verificationId : validationId
  const choices = portfolioChoices(portfolios)

  const choose = (id: string): void => {
    changeSettings(live.settings, kind === 'verification' ? { verificationPortfolioId: id } : { validationPortfolioId: id })
  }

  const kindButton = (value: PortfolioKind, label: string, id: string | null): React.JSX.Element => {
    return (
      <button
        type="button"
        className="settings-kind"
        aria-pressed={kind === value}
        onClick={() => {
          setKind(value)
        }}
      >
        <span className="settings-kind-label">{label}</span>
        <span className="settings-kind-value">{portfolioName(portfolios, id)}</span>
      </button>
    )
  }

  return (
    <Popover.Root>
      <Popover.Trigger className="button button-icon" title="Portfolios and validation" aria-label="Portfolios and validation">
        <Icon name="sliders" />
      </Popover.Trigger>
      <Popover.Portal>
        <Popover.Positioner className="floating" side="bottom" align="end" sideOffset={6}>
          <Popover.Popup className="menu settings-popover">
            <Popover.Title className="visually-hidden">Portfolios and validation</Popover.Title>
            <div className="settings-kinds">
              {kindButton('verification', 'Verification', verificationId)}
              {kindButton('validation', 'Validation', validationId)}
            </div>
            <div role="menu" aria-label={kind === 'verification' ? 'Verification portfolio' : 'Validation portfolio'}>
              {choices.combined.map((choice) => {
                return (
                  <PortfolioItem
                    key={choice.id}
                    choice={choice}
                    checked={choice.id === current}
                    onChoose={() => {
                      choose(choice.id)
                    }}
                  />
                )
              })}
              {choices.singleEngine.length > 0 ? <div className="menu-heading">Single engine</div> : null}
              {choices.singleEngine.map((choice) => {
                return (
                  <PortfolioItem
                    key={choice.id}
                    choice={choice}
                    checked={choice.id === current}
                    onChoose={() => {
                      choose(choice.id)
                    }}
                  />
                )
              })}
            </div>
            <div className="menu-divider" />
            <button
              type="button"
              className="switch-row switch-row-wide"
              role="switch"
              aria-checked={settings.autoValidate}
              onClick={() => {
                changeSettings(live.settings, { autoValidate: !settings.autoValidate })
              }}
            >
              <span className="switch" />
              <span>validate automatically</span>
            </button>
          </Popover.Popup>
        </Popover.Positioner>
      </Popover.Portal>
    </Popover.Root>
  )
}
