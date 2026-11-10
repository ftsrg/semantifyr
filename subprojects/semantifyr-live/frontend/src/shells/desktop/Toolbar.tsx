/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import type React from 'react'
import { Menu } from '@base-ui/react/menu'

import type { LiveExample, LiveFlavor } from '../../examples'
import { LANGUAGE_ORDER, presentationOf } from '../../lib/language/presentation'
import type { LanguageUnit } from '../../lib/language/types'
import { changeSettings } from '../../lib/stores/settings'
import { useStore } from '../../lib/stores/useStore'
import { documentKey } from '../../lib/workspace/live'
import { useLive } from '../../lib/workspace/liveContext'
import type { ColorModePreference } from '../../lib/util/colorMode'
import { Icon, type IconName } from '../../ui/icons'
import { LanguageMark, Logo } from '../../ui/marks'
import { useCurrentDocument, useFlavor } from '../hooks'
import { AboutDialog, DeveloperDialog } from './Dialogs'
import { ConnectionIndicator } from './ConnectionIndicator'
import { OpenButton } from './keeping'
import { SharePopover } from './SharePopover'

const FLAVOUR_TEXTS: Readonly<Record<string, { label: string, detail: string }>> = {
  'oxsts': { label: 'Plain', detail: 'OXSTS on its own.' },
  'oxsts-with-gamma-library': { label: 'With the Gamma library', detail: 'Gamma\'s semantics as an OXSTS library to build on.' },
  'oxsts-with-sysmlv2-library': { label: 'With the SysML v2 library', detail: 'SysML v2\'s semantics as an OXSTS library to build on.' },
}

export function flavourText(flavor: LiveFlavor): { label: string, detail: string } {
  return FLAVOUR_TEXTS[flavor.id] ?? { label: flavor.displayName, detail: flavor.description }
}

function orderedUnits(units: readonly LanguageUnit[]): LanguageUnit[] {
  const rank = (unit: LanguageUnit): number => {
    const index = LANGUAGE_ORDER.indexOf(unit.id)
    return index === -1 ? LANGUAGE_ORDER.length : index
  }
  return [...units].sort((left, right) => {
    return rank(left) - rank(right)
  })
}

function LanguageSwitch(): React.JSX.Element | null {
  const live = useLive()
  const registry = useStore(live.catalog, (catalog) => {
    return catalog.registry
  })
  const flavor = useFlavor(live)
  if (!registry) {
    return null
  }
  const flavors = flavor ? registry.unitOf(flavor.id)?.flavors ?? [] : []
  return (
    <div className="language-switch">
      <div className="segmented" role="group" aria-label="Language">
        {orderedUnits(registry.units).map((unit) => {
          const presentation = presentationOf(unit.id)
          const first = unit.flavors[0]
          return (
            <button
              key={unit.id}
              type="button"
              className="segmented-option"
              data-language={unit.id}
              aria-pressed={flavor?.languageId === unit.id}
              title={presentation.detail}
              onClick={() => {
                if (first && flavor?.languageId !== unit.id) {
                  live.selectModel(first.id, first.examples[0]?.id ?? '')
                }
              }}
            >
              <LanguageMark languageId={unit.id} />
              {presentation.name}
            </button>
          )
        })}
      </div>
      {flavor && flavors.length > 1 ? <FlavourMenu flavor={flavor} flavors={flavors} /> : null}
    </div>
  )
}

function FlavourMenu({ flavor, flavors }: { flavor: LiveFlavor, flavors: readonly LiveFlavor[] }): React.JSX.Element {
  const live = useLive()
  return (
    <Menu.Root>
      <Menu.Trigger className="button button-quiet flavour-button" title={`${presentationOf(flavor.languageId).name} flavour`}>
        {flavourText(flavor).label}
        <Icon name="chevron-down" size="small" />
      </Menu.Trigger>
      <Menu.Portal>
        <Menu.Positioner className="floating" side="bottom" align="start" sideOffset={6}>
          <Menu.Popup className="menu flavour-menu">
            <Menu.Group>
              <Menu.GroupLabel className="menu-heading">{`${presentationOf(flavor.languageId).name} flavour`}</Menu.GroupLabel>
              {flavors.map((candidate) => {
                const text = flavourText(candidate)
                const current = candidate.id === flavor.id
                return (
                  <Menu.Item
                    key={candidate.id}
                    className={`menu-item${current ? ' is-current' : ''}`}
                    onClick={() => {
                      if (!current) {
                        live.selectModel(candidate.id, candidate.examples[0]?.id ?? '')
                      }
                    }}
                  >
                    <span className={`radio${current ? ' is-checked' : ''}`} />
                    <span className="menu-item-label">{text.label}</span>
                    <span className="menu-item-detail">{text.detail}</span>
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

function caseCountOf(live: ReturnType<typeof useLive>, flavor: LiveFlavor, example: LiveExample): number | null {
  const document = live.documents.get().get(`${flavor.id}#${example.id}`)
  if (!document) {
    return null
  }
  const state = live.verification.get().get(document.key)
  return state?.discovery === 'discovered' ? state.cases.length : null
}

function ExampleMenu(): React.JSX.Element | null {
  const live = useLive()
  const flavor = useFlavor(live)
  const document = useCurrentDocument(live)
  useStore(live.verification)
  if (!flavor || !document) {
    return null
  }
  const presentation = presentationOf(flavor.languageId)
  const examples = flavor.examples.filter((example) => {
    return example.id !== 'blank'
  })
  const blank = flavor.examples.find((example) => {
    return example.id === 'blank'
  })
  const kept = document.shared ? undefined : live.documents.get().get(documentKey(flavor.id, '', true))
  const currentId = document.shared ? null : document.example.id
  const triggerLabel = document.file?.name ?? (document.shared ? 'Shared model' : document.example.label)
  return (
    <Menu.Root>
      <Menu.Trigger className="button button-quiet example-button">
        {triggerLabel}
        <span className="chevron">
          <Icon name="chevron-down" size="small" />
        </span>
      </Menu.Trigger>
      <Menu.Portal>
        <Menu.Positioner className="floating" side="bottom" align="start" sideOffset={6}>
          <Menu.Popup className="menu examples-menu" data-language={flavor.languageId}>
            <div className="language-intro">
              <LanguageMark languageId={flavor.languageId} />
              <span className="language-intro-name">{`${presentation.name} examples`}</span>
              <span className="language-intro-detail">{presentation.detail}</span>
            </div>
            <Menu.Separator className="menu-divider" />
            {kept
              ? (
                <>
                  <Menu.Item
                    className="menu-item"
                    onClick={() => {
                      live.showKept(flavor.id)
                    }}
                  >
                    <span>
                      <Icon name="file" size="small" />
                    </span>
                    <span className="menu-item-label">{kept.file?.name ?? 'Shared model'}</span>
                    <span />
                  </Menu.Item>
                  <Menu.Separator className="menu-divider" />
                </>
              )
              : null}
            {examples.map((example) => {
              const current = example.id === currentId
              const count = caseCountOf(live, flavor, example)
              return (
                <Menu.Item
                  key={example.id}
                  className={`menu-item${current ? ' is-current' : ''}`}
                  onClick={() => {
                    live.selectModel(flavor.id, example.id)
                  }}
                >
                  <span>{current ? <span className="check"><Icon name="check" size="small" /></span> : null}</span>
                  <span className="menu-item-label">{example.label}</span>
                  <span className="menu-item-meta">{count === null ? '' : `${count} ${count === 1 ? 'case' : 'cases'}`}</span>
                  <span className="menu-item-detail">{example.description}</span>
                </Menu.Item>
              )
            })}
            {blank
              ? (
                <>
                  <Menu.Separator className="menu-divider" />
                  <Menu.Item
                    className={`menu-item${blank.id === currentId ? ' is-current' : ''}`}
                    onClick={() => {
                      live.selectModel(flavor.id, blank.id)
                    }}
                  >
                    <span>
                      <Icon name="file" size="small" />
                    </span>
                    <span className="menu-item-label">Blank model</span>
                    <span />
                  </Menu.Item>
                </>
              )
              : null}
          </Menu.Popup>
        </Menu.Positioner>
      </Menu.Portal>
    </Menu.Root>
  )
}

const THEME_CHOICES: readonly { value: ColorModePreference, icon: IconName, label: string }[] = [
  { value: 'system', icon: 'monitor', label: 'System' },
  { value: 'light', icon: 'sun', label: 'Light' },
  { value: 'dark', icon: 'moon', label: 'Dark' },
]

function ThemeSwitch(): React.JSX.Element {
  const live = useLive()
  const theme = useStore(live.settings, (settings) => {
    return settings.theme
  })
  return (
    <div className="segmented segmented-small theme-switch" role="group" aria-label="Theme">
      {THEME_CHOICES.map((choice) => {
        return (
          <button
            key={choice.value}
            type="button"
            className="segmented-option"
            aria-pressed={theme === choice.value}
            title={choice.label}
            aria-label={choice.label}
            onClick={() => {
              changeSettings(live.settings, { theme: choice.value })
            }}
          >
            <Icon name={choice.icon} size="small" />
          </button>
        )
      })}
    </div>
  )
}

export function Toolbar(): React.JSX.Element {
  return (
    <header className="toolbar">
      <div className="toolbar-group">
        <div className="brand">
          <Logo />
          <ConnectionIndicator />
        </div>
        <LanguageSwitch />
        <span className="separator" />
        <ExampleMenu />
      </div>
      <div className="toolbar-group toolbar-group-end">
        <OpenButton />
        <SharePopover />
        <AboutDialog />
        <DeveloperDialog />
        <span className="separator" />
        <ThemeSwitch />
      </div>
    </header>
  )
}
