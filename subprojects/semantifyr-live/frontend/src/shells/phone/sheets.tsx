/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import type React from 'react'
import { useState } from 'react'
import { Dialog } from '@base-ui/react/dialog'
import { LanguageMark } from '@semantifyr/editor-common/marks'

import { languagePageOf, listedExamples, type LiveExample, type LiveFlavor } from '../../examples'
import { LANGUAGE_ORDER, presentationOf } from '../../lib/language/presentation'
import type { LanguageUnit } from '../../lib/language/types'
import { changeSettings } from '../../lib/stores/settings'
import { useStore } from '../../lib/stores/useStore'
import type { ColorModePreference, ResolvedColorMode } from '../../lib/util/colorMode'
import type { Live } from '../../lib/workspace/live'
import { useCurrentVerification, useLive } from '../../lib/workspace/liveContext'
import { portfolioChoices, portfolioName, type PortfolioChoice } from '../../lib/workspace/portfolioChoices'
import { sharedResultsOf } from '../../lib/workspace/sharedResults'
import { aboutLanguage, BLANK_MODEL, LIBRARY, libraryOf, NO_PROBLEMS, ONE_BACKEND, PROBLEMS, THEMES, VALIDATION, websitePage } from '../../texts'
import { Icon, type IconName } from '../../ui/icons'
import { AboutDialog, DeveloperDialog } from '../desktop/Dialogs'
import { pickFile } from '../desktop/keeping'
import { shareLink } from '../desktop/SharePopover'
import { useCurrentDocument, useFlavor } from '../hooks'

export type Overlay =
  | 'chooser'
  | 'menu'
  | 'verification'
  | 'validation'
  | 'problems'

interface SheetProps {
  open: boolean
  onClose: () => void
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

function caseCountOf(live: Live, flavor: LiveFlavor, example: LiveExample): number | null {
  const document = live.documents.get().get(`${flavor.id}#${example.id}`)
  const state = document ? live.verification.get().get(document.key) : undefined
  return state?.discovery === 'discovered' ? state.cases.length : null
}

function Sheet({ open, onClose, className, label, children }: SheetProps & { className: string, label: string, children: React.ReactNode }): React.JSX.Element {
  return (
    <Dialog.Root
      open={open}
      onOpenChange={(opened) => {
        if (!opened) {
          onClose()
        }
      }}
    >
      <Dialog.Portal>
        <Dialog.Backdrop className="scrim phone-scrim" />
        <Dialog.Popup className={className} aria-label={label}>
          {children}
        </Dialog.Popup>
      </Dialog.Portal>
    </Dialog.Root>
  )
}

function Chooser({ onClose }: { onClose: () => void }): React.JSX.Element | null {
  const live = useLive()
  const registry = useStore(live.catalog, (catalog) => {
    return catalog.registry
  })
  const current = useFlavor(live)
  const document = useCurrentDocument(live)
  useStore(live.verification)
  const [chosen, setChosen] = useState<LiveFlavor | null>(current)
  if (!registry || !chosen) {
    return null
  }
  const unit = registry.unitOf(chosen.id)
  const libraryDetail = libraryOf(chosen.id).detail
  const presentation = presentationOf(chosen.languageId)
  const examples = listedExamples(chosen)
  const languagePage = languagePageOf(chosen.languageId)
  const blank = chosen.examples.find((example) => {
    return example.id === 'blank'
  })
  const currentId = document && !document.shared && current?.id === chosen.id ? document.example.id : null
  const choose = (example: LiveExample): void => {
    live.selectModel(chosen.id, example.id)
    onClose()
  }
  return (
    <>
      <div className="sheet-header">
        <Dialog.Title className="sheet-title">Choose a model</Dialog.Title>
        <Dialog.Close className="phone-icon-button" aria-label="Close">
          <Icon name="close" />
        </Dialog.Close>
      </div>
      <div className="segmented chooser-languages" role="group" aria-label="Language">
        {orderedUnits(registry.units).map((candidate) => {
          return (
            <button
              key={candidate.id}
              type="button"
              className="segmented-option"
              data-language={candidate.id}
              aria-pressed={candidate.id === chosen.languageId}
              onClick={() => {
                const first = current?.languageId === candidate.id ? current : candidate.flavors[0]
                if (first) {
                  setChosen(first)
                }
              }}
            >
              <LanguageMark languageId={candidate.id} />
              {presentationOf(candidate.id).name}
            </button>
          )
        })}
      </div>
      {unit && unit.flavors.length > 1
        ? (
          <div className="chooser-flavours">
            <span className="chooser-flavours-label">{LIBRARY.heading}</span>
            <div className="segmented segmented-plain" role="group" aria-label={LIBRARY.heading}>
              {unit.flavors.map((flavor) => {
                return (
                  <button
                    key={flavor.id}
                    type="button"
                    className="segmented-option"
                    aria-pressed={flavor.id === chosen.id}
                    onClick={() => {
                      setChosen(flavor)
                    }}
                  >
                    {libraryOf(flavor.id).label}
                  </button>
                )
              })}
            </div>
            {libraryDetail === null ? null : <span className="chooser-library-detail">{libraryDetail}</span>}
          </div>
        )
        : null}
      <p className="chooser-intro">
        {presentation.detail}
        {languagePage === null
          ? null
          : (
            <>
              {' '}
              <a href={websitePage(languagePage)} target="_blank" rel="noreferrer">{aboutLanguage(presentation.name)}</a>
            </>
          )}
      </p>
      <div className="chooser-list" data-language={chosen.languageId}>
        <div className="section-label">{`${presentation.name} examples`}</div>
        {examples.map((example) => {
          const isCurrent = example.id === currentId
          const count = caseCountOf(live, chosen, example)
          return (
            <button
              key={example.id}
              type="button"
              className={`chooser-item${isCurrent ? ' is-current' : ''}`}
              onClick={() => {
                choose(example)
              }}
            >
              <span className="chooser-check">{isCurrent ? <Icon name="check" size="small" /> : null}</span>
              <span className="chooser-label">{example.label}</span>
              <span className="chooser-count">{count === null ? '' : `${count} ${count === 1 ? 'case' : 'cases'}`}</span>
              {example.description === null ? null : <span className="chooser-detail">{example.description}</span>}
            </button>
          )
        })}
        {blank
          ? (
            <>
              <div className="chooser-divider" />
              <button
                type="button"
                className={`chooser-item${blank.id === currentId ? ' is-current' : ''}`}
                onClick={() => {
                  choose(blank)
                }}
              >
                <span className="chooser-check">
                  <Icon name="file" size="small" />
                </span>
                <span className="chooser-label">{BLANK_MODEL}</span>
              </button>
            </>
          )
          : null}
      </div>
    </>
  )
}

export function ChooserSheet({ open, onClose }: SheetProps): React.JSX.Element {
  const live = useLive()
  const languageId = useStore(live.workspace, (workspace) => {
    return workspace.flavor?.languageId
  })
  return (
    <Sheet open={open} onClose={onClose} className="sheet-full" label="Choose a model">
      {open ? <Chooser key={languageId} onClose={onClose} /> : null}
    </Sheet>
  )
}

const THEME_CHOICES: readonly { value: ColorModePreference, label: string }[] = [
  { value: 'system', label: THEMES.system },
  { value: 'light', label: THEMES.light },
  { value: 'dark', label: THEMES.dark },
]

function MenuEntry({ icon, label, detail, onClick }: { icon: IconName, label: string, detail?: string, onClick: () => void }): React.JSX.Element {
  return (
    <button type="button" className="sheet-item" onClick={onClick}>
      <Icon name={icon} />
      <span>{label}</span>
      {detail !== undefined ? <span className="sheet-item-detail">{detail}</span> : null}
    </button>
  )
}

// the phone's own share sheet takes the link where it has one; elsewhere the link is copied
async function share(live: Live): Promise<'shared' | 'copied' | 'failed'> {
  const link = await shareLink(live, true)
  if (link === null) {
    return 'failed'
  }
  try {
    if (typeof navigator.share === 'function') {
      await navigator.share({ title: document.title, url: link })
      return 'shared'
    }
    await navigator.clipboard.writeText(link)
    return 'copied'
  } catch {
    return 'failed'
  }
}

function ShareEntry({ onClose }: { onClose: () => void }): React.JSX.Element {
  const live = useLive()
  const verification = useCurrentVerification(live)
  const [copied, setCopied] = useState<'copied' | 'failed' | null>(null)
  const withResults = sharedResultsOf(verification, null) !== null
  const detail = copied === 'copied' ? 'Link copied' : copied === 'failed' ? 'Could not share the link' : withResults ? 'Model and results' : 'Model'
  return (
    <MenuEntry
      icon="link"
      label="Share"
      detail={detail}
      onClick={() => {
        void share(live).then((outcome) => {
          if (outcome === 'shared') {
            onClose()
          } else {
            setCopied(outcome)
          }
        })
      }}
    />
  )
}

export function MenuSheet({ open, onClose, onPortfolios, colorMode }: SheetProps & { onPortfolios: (kind: 'verification' | 'validation') => void, colorMode: ResolvedColorMode }): React.JSX.Element {
  const live = useLive()
  const settings = useStore(live.settings)
  const portfolios = useStore(live.catalog, (catalog) => {
    return catalog.portfolios
  })
  const dark = colorMode === 'dark'
  return (
    <Sheet open={open} onClose={onClose} className="sheet-bottom" label="Menu">
      <div className="sheet-grabber" />
      <MenuEntry
        icon="open"
        label="Open a file"
        onClick={() => {
          onClose()
          pickFile(live)
        }}
      />
      {open ? <ShareEntry onClose={onClose} /> : null}
      <MenuEntry
        icon="sliders"
        label="Verification"
        detail={portfolioName(portfolios, live.verificationPortfolioId())}
        onClick={() => {
          onPortfolios('verification')
        }}
      />
      <MenuEntry
        icon="shield-check"
        label="Validation"
        detail={portfolioName(portfolios, settings.validationPortfolioId)}
        onClick={() => {
          onPortfolios('validation')
        }}
      />
      <div className="sheet-item sheet-switch">
        <Icon name="shield-check" />
        <span>{VALIDATION.automatically}</span>
        <button
          type="button"
          className="switch-row"
          role="switch"
          aria-checked={settings.autoValidate}
          aria-label={VALIDATION.automatically}
          onClick={() => {
            changeSettings(live.settings, { autoValidate: !settings.autoValidate })
          }}
        >
          <span className="switch" />
        </button>
      </div>
      <AboutDialog className="sheet-item">
        <Icon name="info" />
        <span>About</span>
      </AboutDialog>
      <DeveloperDialog className="sheet-item">
        <Icon name="terminal" />
        <span>Developer panel</span>
      </DeveloperDialog>
      <div className="sheet-item sheet-theme">
        <Icon name={dark ? 'moon' : 'sun'} />
        <span>Theme</span>
        <div className="segmented segmented-plain" role="group" aria-label="Theme">
          {THEME_CHOICES.map((choice) => {
            return (
              <button
                key={choice.value}
                type="button"
                className="segmented-option"
                aria-pressed={settings.theme === choice.value}
                onClick={() => {
                  changeSettings(live.settings, { theme: choice.value })
                }}
              >
                {choice.label}
              </button>
            )
          })}
        </div>
      </div>
    </Sheet>
  )
}

export function PortfolioSheet({ kind, onClose }: { kind: 'verification' | 'validation' | null, onClose: () => void }): React.JSX.Element {
  const live = useLive()
  const settings = useStore(live.settings)
  const portfolios = useStore(live.catalog, (catalog) => {
    return catalog.portfolios
  })
  const current = kind === 'verification' ? live.verificationPortfolioId() ?? null : settings.validationPortfolioId
  const choices = portfolioChoices(portfolios)
  const item = (choice: PortfolioChoice): React.JSX.Element => {
    const checked = choice.id === current
    return (
      <button
        key={choice.id}
        type="button"
        className="sheet-item portfolio-choice"
        role="menuitemradio"
        aria-checked={checked}
        onClick={() => {
          changeSettings(live.settings, kind === 'verification' ? { verificationPortfolioId: choice.id } : { validationPortfolioId: choice.id })
          onClose()
        }}
      >
        <span className={`radio${checked ? ' is-checked' : ''}`} />
        <span className="portfolio-choice-text">
          <span>{choice.name}</span>
          {choice.detail !== null ? <span className="portfolio-choice-detail">{choice.detail}</span> : null}
        </span>
      </button>
    )
  }
  return (
    <Sheet open={kind !== null} onClose={onClose} className="sheet-bottom" label={kind === 'validation' ? 'Validation portfolio' : 'Verification portfolio'}>
      <div className="sheet-grabber" />
      <Dialog.Title className="sheet-title">{kind === 'validation' ? 'Validation portfolio' : 'Verification portfolio'}</Dialog.Title>
      {kind === 'validation' ? <p className="sheet-note">{VALIDATION.explained}</p> : null}
      <div role="menu">
        {choices.combined.map(item)}
        {choices.singleBackend.length > 0 ? <div className="sheet-title">{ONE_BACKEND}</div> : null}
        {choices.singleBackend.map(item)}
      </div>
    </Sheet>
  )
}

export function ProblemsSheet({ open, onClose, onReveal }: SheetProps & { onReveal: () => void }): React.JSX.Element {
  const live = useLive()
  const problems = useStore(live.problems)
  const listed = problems.filter((problem) => {
    return problem.severity === 'error' || problem.severity === 'warning'
  })
  return (
    <Sheet open={open} onClose={onClose} className="sheet-bottom" label={PROBLEMS}>
      <div className="sheet-grabber" />
      <Dialog.Title className="sheet-title">{PROBLEMS}</Dialog.Title>
      {listed.length === 0 ? <div className="sheet-item">{NO_PROBLEMS}</div> : null}
      {listed.map((problem, index) => {
        return (
          <button
            key={`${problem.startLine}:${problem.startColumn}:${index}`}
            type="button"
            className={`sheet-item problem-entry is-${problem.severity}`}
            onClick={() => {
              onReveal()
              live.revealRange({
                start: { line: problem.startLine, character: problem.startColumn },
                end: { line: problem.endLine, character: problem.endColumn },
              })
            }}
          >
            <Icon name={problem.severity === 'error' ? 'error' : 'alert'} />
            <span>{problem.message}</span>
            <span className="sheet-item-detail">{`Ln ${problem.startLine + 1}`}</span>
          </button>
        )
      })}
    </Sheet>
  )
}
