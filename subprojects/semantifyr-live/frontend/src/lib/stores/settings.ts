/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import {
  persistBool,
  persistSize,
  persistString,
  readPersistedBool,
  readPersistedSize,
  readPersistedString,
} from '../util/persistence'
import type { ColorModePreference, ResolvedColorMode } from '../util/colorMode'
import { DEFAULT_VALIDATION_PORTFOLIO_ID } from '../verification/engine'
import { createStore, type ReadableStore, type Store } from './store'

export interface Settings {
  theme: ColorModePreference
  verificationPortfolioId: string | null
  validationPortfolioId: string
  autoValidate: boolean
  sidePanelOpen: boolean
  sidePanelWidth: number
  resultsPanelOpen: boolean
  resultsPanelHeight: number
  // the results column of the desktop shell; a size of 0 is the layout's default
  resultsColumnWidth: number
  caseListHeight: number
  resultsColumnCollapsed: boolean
}

const SettingsKeys = {
  theme: 'semantifyr-live-color-mode',
  verificationPortfolioId: 'semantifyr-verification-portfolio',
  validationPortfolioId: 'semantifyr-validation-portfolio',
  autoValidate: 'semantifyr-auto-validate',
  sidePanelOpen: 'semantifyr-right-panel-open',
  sidePanelWidth: 'semantifyr-right-pane-width',
  resultsPanelOpen: 'semantifyr-verification-panel-open',
  resultsPanelHeight: 'semantifyr-verification-panel-height',
  resultsColumnWidth: 'semantifyr-results-column-width',
  caseListHeight: 'semantifyr-case-list-height',
  resultsColumnCollapsed: 'semantifyr-results-column-collapsed',
} as const

const THEMES: readonly ColorModePreference[] = [
  'system',
  'light',
  'dark',
]

function readTheme(): ColorModePreference {
  const stored = readPersistedString(SettingsKeys.theme, 'system')
  return THEMES.find((theme) => {
    return theme === stored
  }) ?? 'system'
}

function readSettings(): Settings {
  const verificationPortfolioId = readPersistedString(SettingsKeys.verificationPortfolioId, '')
  return {
    theme: readTheme(),
    verificationPortfolioId: verificationPortfolioId === '' ? null : verificationPortfolioId,
    validationPortfolioId: readPersistedString(SettingsKeys.validationPortfolioId, DEFAULT_VALIDATION_PORTFOLIO_ID),
    autoValidate: readPersistedBool(SettingsKeys.autoValidate, true),
    sidePanelOpen: readPersistedBool(SettingsKeys.sidePanelOpen, false),
    sidePanelWidth: readPersistedSize(SettingsKeys.sidePanelWidth, 480),
    resultsPanelOpen: readPersistedBool(SettingsKeys.resultsPanelOpen, true),
    resultsPanelHeight: readPersistedSize(SettingsKeys.resultsPanelHeight, 220),
    resultsColumnWidth: readPersistedSize(SettingsKeys.resultsColumnWidth, 0),
    caseListHeight: readPersistedSize(SettingsKeys.caseListHeight, 0),
    resultsColumnCollapsed: readPersistedBool(SettingsKeys.resultsColumnCollapsed, false),
  }
}

function writeSettings(settings: Settings): void {
  persistString(SettingsKeys.theme, settings.theme)
  persistString(SettingsKeys.verificationPortfolioId, settings.verificationPortfolioId ?? '')
  persistString(SettingsKeys.validationPortfolioId, settings.validationPortfolioId)
  persistBool(SettingsKeys.autoValidate, settings.autoValidate)
  persistBool(SettingsKeys.sidePanelOpen, settings.sidePanelOpen)
  persistSize(SettingsKeys.sidePanelWidth, settings.sidePanelWidth)
  persistBool(SettingsKeys.resultsPanelOpen, settings.resultsPanelOpen)
  persistSize(SettingsKeys.resultsPanelHeight, settings.resultsPanelHeight)
  persistSize(SettingsKeys.resultsColumnWidth, settings.resultsColumnWidth)
  persistSize(SettingsKeys.caseListHeight, settings.caseListHeight)
  persistBool(SettingsKeys.resultsColumnCollapsed, settings.resultsColumnCollapsed)
}

export function createSettingsStore(): Store<Settings> {
  const store = createStore(readSettings())
  store.subscribe(() => {
    writeSettings(store.get())
  })
  return store
}

export function changeSettings(store: Store<Settings>, change: Partial<Settings>): void {
  store.update((previous) => {
    return { ...previous, ...change }
  })
}

export function nextTheme(theme: ColorModePreference): ColorModePreference {
  return THEMES[(THEMES.indexOf(theme) + 1) % THEMES.length] ?? 'system'
}

export function resolveColorMode(theme: ColorModePreference, system: ResolvedColorMode): ResolvedColorMode {
  return theme === 'system' ? system : theme
}

export function createSystemColorModeStore(): ReadableStore<ResolvedColorMode> {
  const query = typeof window === 'undefined' ? null : window.matchMedia('(prefers-color-scheme: light)')
  const store = createStore<ResolvedColorMode>(query?.matches ? 'light' : 'dark')
  query?.addEventListener('change', (event) => {
    store.set(event.matches ? 'light' : 'dark')
  })
  return store
}
