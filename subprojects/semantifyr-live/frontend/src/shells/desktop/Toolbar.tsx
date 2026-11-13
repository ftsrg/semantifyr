/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import type React from 'react'
import { Menu } from '@base-ui/react/menu'
import { liveEditorUrl, parseLiveEditorUrl } from '@semantifyr/editor-common'
import { LanguageMark } from '@semantifyr/editor-common/marks'

import { languagePageOf, listedExamples, type LiveExample, type LiveFlavor } from '../../examples'
import { LANGUAGE_ORDER, presentationOf } from '../../lib/language/presentation'
import type { LanguageUnit } from '../../lib/language/types'
import { changeSettings } from '../../lib/stores/settings'
import { useStore } from '../../lib/stores/useStore'
import { documentKey, type Live } from '../../lib/workspace/live'
import { useLive } from '../../lib/workspace/liveContext'
import type { ColorModePreference } from '../../lib/util/colorMode'
import { aboutLanguage, BLANK_MODEL, LIBRARY, libraryButton, libraryOf, SHARED_MODEL, THEMES, websitePage } from '../../texts'
import { Icon, type IconName } from '../../ui/icons'
import { Logo } from '../../ui/marks'
import { useAddressedModel, useCurrentDocument, useFlavor } from '../hooks'
import { AboutDialog, DeveloperDialog } from './Dialogs'
import { ConnectionIndicator } from './ConnectionIndicator'
import { OpenButton, SaveSplit } from './keeping'
import { SharePopover } from './SharePopover'

function orderedUnits(units: readonly LanguageUnit[]): LanguageUnit[] {
  const rank = (unit: LanguageUnit): number => {
    const index = LANGUAGE_ORDER.indexOf(unit.id)
    return index === -1 ? LANGUAGE_ORDER.length : index
  }
  return [...units].sort((left, right) => {
    return rank(left) - rank(right)
  })
}

// while the server does not answer, a switch moves the page to the address, which opens once it answers
function openWaitingAddress(live: Live, language: string, example: string | undefined, library: string | undefined): void {
  const address = parseLiveEditorUrl(window.location)
  void liveEditorUrl(window.location.href, {
    language,
    example,
    library,
    view: address.view ?? undefined,
    frame: address.frame ?? undefined,
  }).then((url) => {
    window.history.pushState(null, '', url)
    live.openAddress(url)
  })
}

function WaitingLanguageSwitch(): React.JSX.Element {
  const live = useLive()
  const model = useAddressedModel(live)
  return (
    <div className="language-switch">
      <div className="segmented" role="group" aria-label="Language">
        {LANGUAGE_ORDER.map((languageId) => {
          const presentation = presentationOf(languageId)
          return (
            <button
              key={languageId}
              type="button"
              className="segmented-option"
              data-language={languageId}
              aria-pressed={model.languageId === languageId}
              title={presentation.detail}
              onClick={() => {
                if (model.languageId !== languageId) {
                  openWaitingAddress(live, languageId, undefined, undefined)
                }
              }}
            >
              <LanguageMark languageId={languageId} />
              {presentation.name}
            </button>
          )
        })}
      </div>
    </div>
  )
}

function LanguageSwitch(): React.JSX.Element | null {
  const live = useLive()
  const registry = useStore(live.catalog, (catalog) => {
    return catalog.registry
  })
  const flavor = useFlavor(live)
  if (!registry) {
    return <WaitingLanguageSwitch />
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
      <Menu.Trigger className="button button-quiet flavour-button" title={LIBRARY.heading}>
        {libraryButton(flavor.id)}
        <Icon name="chevron-down" size="small" />
      </Menu.Trigger>
      <Menu.Portal>
        <Menu.Positioner className="floating" side="bottom" align="start" sideOffset={6}>
          <Menu.Popup className="menu flavour-menu">
            <Menu.Group>
              <Menu.GroupLabel className="menu-heading">{LIBRARY.heading}</Menu.GroupLabel>
              {flavors.map((candidate) => {
                const text = libraryOf(candidate.id)
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
                    {text.detail === null ? null : <span className="menu-item-detail">{text.detail}</span>}
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

// the language the menu's examples are written in, with the website's page on it
function LanguageIntro({ languageId }: { languageId: string }): React.JSX.Element {
  const presentation = presentationOf(languageId)
  const page = languagePageOf(languageId)
  return (
    <div className="language-intro">
      <LanguageMark languageId={languageId} />
      <span className="language-intro-name">{`${presentation.name} examples`}</span>
      <span className="language-intro-detail">{presentation.detail}</span>
      {page === null ? null : <a className="link language-intro-page" href={websitePage(page)} target="_blank" rel="noreferrer">{aboutLanguage(presentation.name)}</a>}
    </div>
  )
}

function WaitingExampleMenu(): React.JSX.Element {
  const live = useLive()
  const model = useAddressedModel(live)
  const triggerLabel = model.example?.label ?? SHARED_MODEL
  return (
    <Menu.Root>
      <Menu.Trigger className="button button-quiet example-button" title={triggerLabel}>
        <span className="example-label">{triggerLabel}</span>
        <span className="chevron">
          <Icon name="chevron-down" size="small" />
        </span>
      </Menu.Trigger>
      <Menu.Portal>
        <Menu.Positioner className="floating" side="bottom" align="start" sideOffset={6}>
          <Menu.Popup className="menu examples-menu" data-language={model.languageId}>
            <LanguageIntro languageId={model.languageId} />
            <Menu.Separator className="menu-divider" />
            {model.examples.filter((example) => {
              return example.served?.hidden !== true
            }).map((example) => {
              const current = example.id === model.example?.id
              return (
                <Menu.Item
                  key={example.id}
                  className={`menu-item${current ? ' is-current' : ''}`}
                  onClick={() => {
                    openWaitingAddress(live, model.languageId, example.id, model.library)
                  }}
                >
                  <span>{current ? <span className="check"><Icon name="check" size="small" /></span> : null}</span>
                  <span className="menu-item-label">{example.id === 'blank' ? BLANK_MODEL : example.label}</span>
                  <span className="menu-item-meta" />
                  {example.description === null ? null : <span className="menu-item-detail">{example.description}</span>}
                </Menu.Item>
              )
            })}
          </Menu.Popup>
        </Menu.Positioner>
      </Menu.Portal>
    </Menu.Root>
  )
}

function ExampleMenu(): React.JSX.Element | null {
  const live = useLive()
  const flavor = useFlavor(live)
  const document = useCurrentDocument(live)
  const waiting = useStore(live.catalog, (catalog) => {
    return catalog.status === 'waiting'
  })
  useStore(live.verification)
  if (waiting) {
    return <WaitingExampleMenu />
  }
  if (!flavor || !document) {
    return null
  }
  const examples = listedExamples(flavor)
  const blank = flavor.examples.find((example) => {
    return example.id === 'blank'
  })
  const kept = document.shared ? undefined : live.documents.get().get(documentKey(flavor.id, '', true))
  const currentId = document.shared ? null : document.example.id
  const triggerLabel = document.file?.name ?? (document.shared ? SHARED_MODEL : document.example.label)
  return (
    <Menu.Root>
      <Menu.Trigger className="button button-quiet example-button" title={triggerLabel}>
        <span className="example-label">{triggerLabel}</span>
        <span className="chevron">
          <Icon name="chevron-down" size="small" />
        </span>
      </Menu.Trigger>
      <Menu.Portal>
        <Menu.Positioner className="floating" side="bottom" align="start" sideOffset={6}>
          <Menu.Popup className="menu examples-menu" data-language={flavor.languageId}>
            <LanguageIntro languageId={flavor.languageId} />
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
                    <span className="menu-item-label">{kept.file?.name ?? SHARED_MODEL}</span>
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
                  {example.description === null ? null : <span className="menu-item-detail">{example.description}</span>}
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
                    <span className="menu-item-label">{BLANK_MODEL}</span>
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
  { value: 'system', icon: 'monitor', label: THEMES.system },
  { value: 'light', icon: 'sun', label: THEMES.light },
  { value: 'dark', icon: 'moon', label: THEMES.dark },
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
        <SaveSplit />
        <SharePopover />
        <AboutDialog />
        <DeveloperDialog />
        <span className="separator" />
        <ThemeSwitch />
      </div>
    </header>
  )
}
