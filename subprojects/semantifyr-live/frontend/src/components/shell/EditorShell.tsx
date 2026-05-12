/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import React from 'react'
import Box from '@mui/material/Box'

import Toolbar from './Toolbar'
import StatusBar, { type StatusBarInfoItem } from './StatusBar'
import DevInfoPanel from '../dev/DevInfoPanel'
import ActiveVerificationsMonitor from '../verification/ActiveVerificationsMonitor'
import PortfolioSettings from '../verification/PortfolioSettings'
import type { LiveEditorHandle, LiveEditorStatus } from '../editor/LiveEditor'
import type { ColorModePreference } from '../../lib/util/colorMode'
import type { LiveFlavor } from '../../examples'
import type { FlavorInfo, PortfolioInfo } from '../../lib/api'

/**
 * Shell that wraps the editor body with toolbar, status bar, and the developer info panel.
 * The {@code ?embed=1} entry point bypasses this component and mounts the body directly, so
 * the body stays the same shape regardless of whether chrome is visible.
 *
 * <p>Lives in {@code shell/} alongside the rest of the chrome pieces. EditorPage owns the
 * underlying state (verification, flavor, etc.) and threads the relevant props in.
 */
interface Props {
  // Toolbar
  logoSrc: string
  flavors: readonly LiveFlavor[]
  currentFlavorId: string
  currentExampleId: string
  onSelectModel: (flavorId: string, exampleId: string) => void
  onCopyLink: () => void
  copyConfirmation: string | null
  // Connection
  connectionStatus: LiveEditorStatus
  onReconnect: () => void
  onDisconnect: () => void
  // Status bar
  statusBarMessage: string | null
  statusBarShowProgress: boolean
  statusBarInfoItems: readonly StatusBarInfoItem[]
  onStatusInfoClick: () => void
  onOpenVerificationsTab: () => void
  // Portfolio
  portfolios: readonly PortfolioInfo[]
  portfolioId: string
  onPortfolioChange: (id: string) => void
  validationPortfolioId: string
  onValidationPortfolioChange: (id: string) => void
  flavorInfo: FlavorInfo | null
  // Color mode
  colorModePreference: ColorModePreference
  onToggleColorMode: () => void
  // Dev panel
  devPanelOpen: boolean
  onCloseDevPanel: () => void
  language: string
  connectedSince: number | null
  reconnectCount: number
  editorHandle: LiveEditorHandle | null
  backendUrl: string
  // Body
  children: React.ReactNode
}

export default function EditorShell(props: Props): React.JSX.Element {
  return (
    <>
      <Toolbar
        logoSrc={props.logoSrc}
        flavors={props.flavors}
        currentFlavorId={props.currentFlavorId}
        currentExampleId={props.currentExampleId}
        onSelectModel={props.onSelectModel}
        connectionStatus={props.connectionStatus}
        onReconnect={props.onReconnect}
        onDisconnect={props.onDisconnect}
        onCopyLink={props.onCopyLink}
        copyConfirmation={props.copyConfirmation}
        colorModePreference={props.colorModePreference}
        onToggleColorMode={props.onToggleColorMode}
      />
      {props.children}
      <StatusBar
        message={props.statusBarMessage}
        showProgress={props.statusBarShowProgress}
        infoItems={props.statusBarInfoItems}
        onInfoClick={props.onStatusInfoClick}
        trailing={
          <Box sx={{ display: 'inline-flex', alignItems: 'center', gap: 0.25 }}>
            <ActiveVerificationsMonitor
              api={props.editorHandle?.getApi() ?? null}
              connected={props.connectionStatus === 'connected'}
              onActivate={props.onOpenVerificationsTab}
            />
            {props.portfolios.length > 0 && (
              <PortfolioSettings
                portfolios={props.portfolios}
                verifyPortfolioId={props.portfolioId}
                onVerifyPortfolioChange={props.onPortfolioChange}
                validationPortfolioId={
                  props.flavorInfo?.validateWitnessCommand ? props.validationPortfolioId : null
                }
                onValidationPortfolioChange={props.onValidationPortfolioChange}
              />
            )}
          </Box>
        }
      />
      <DevInfoPanel
        open={props.devPanelOpen}
        onClose={props.onCloseDevPanel}
        connectionStatus={props.connectionStatus}
        language={props.language}
        connectedSince={props.connectedSince}
        reconnectCount={props.reconnectCount}
        editorHandle={props.editorHandle}
        backendUrl={props.backendUrl}
      />
    </>
  )
}
